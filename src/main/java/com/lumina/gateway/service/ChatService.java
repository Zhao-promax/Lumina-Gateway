package com.lumina.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.api.dto.ChatChunk;
import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.audit.AuditEvent;
import com.lumina.gateway.budget.BudgetService;
import com.lumina.gateway.budget.BudgetWarningService;
import com.lumina.gateway.cache.ExactCacheService;
import com.lumina.gateway.cache.SemanticCacheService;
import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.exception.GatewayException;
import com.lumina.gateway.metrics.GatewayObserver;
import com.lumina.gateway.moderation.ContentModerationService;
import com.lumina.gateway.provider.ProviderRegistry;
import com.lumina.gateway.provider.spi.ModelProvider;
import com.lumina.gateway.ratelimit.RateLimiter;
import com.lumina.gateway.resilience.ResilienceManager;
import com.lumina.gateway.routing.RouteResolver;
import com.lumina.gateway.routing.RouteTarget;
import com.lumina.gateway.usage.TokenEstimator;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网关编排：路由 → 限流 → 内容风控 → 预算预扣 → 双层缓存 → 主备故障转移/降级 → 结算 → 标准化。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String DONE_FRAME = "data: [DONE]\n\n";

    private final ProviderRegistry registry;
    private final RouteResolver router;
    private final ResilienceManager resilienceManager;
    private final GatewayProperties properties;
    private final RateLimiter rateLimiter;
    private final BudgetService budgetService;
    private final BudgetWarningService budgetWarning;
    private final ExactCacheService exactCache;
    private final Optional<SemanticCacheService> semanticCache;
    private final GatewayObserver observer;
    private final ContentModerationService moderationService;
    private final ObjectMapper objectMapper;

    public ChatService(ProviderRegistry registry,
                       RouteResolver router,
                       ResilienceManager resilienceManager,
                       GatewayProperties properties,
                       RateLimiter rateLimiter,
                       BudgetService budgetService,
                       BudgetWarningService budgetWarning,
                       ExactCacheService exactCache,
                       Optional<SemanticCacheService> semanticCache,
                       GatewayObserver observer,
                       ContentModerationService moderationService,
                       ObjectMapper objectMapper) {
        this.registry = registry;
        this.router = router;
        this.resilienceManager = resilienceManager;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.budgetService = budgetService;
        this.budgetWarning = budgetWarning;
        this.exactCache = exactCache;
        this.semanticCache = semanticCache;
        this.observer = observer;
        this.moderationService = moderationService;
        this.objectMapper = objectMapper;
    }

    // ===== 非流式 =====

    public Mono<ChatResponse> complete(ChatRequest request, String routeTag, String tenant, String appKey) {
        String traceId = UUID.randomUUID().toString();
        Timer.Sample sample = observer.start();
        long startMs = System.currentTimeMillis();

        return resolveCandidates(routeTag, tenant, appKey)
                .flatMap(candidates -> {
                    if (candidates.isEmpty()) {
                        return Mono.error(new GatewayException(HttpStatus.BAD_REQUEST, "no_route", "no candidates"));
                    }
                    String provider = candidates.get(0).provider();
                    String model = candidates.get(0).model();
                    return rateLimitCheck(candidates, tenant, appKey)
                            .then(moderationCheck(request))
                            .then(Mono.defer(() -> {
                                long estimate = TokenEstimator.estimate(request, properties.getBudget().getDefaultCompletionTokens());
                                return reserve(tenant, appKey, estimate)
                                        .flatMap(reserved -> reserved
                                                ? cachedOrCall(candidates, request, tenant, appKey, model, estimate)
                                                        .onErrorResume(e -> refund(tenant, appKey, estimate, e))
                                                : Mono.error(budgetExceeded()));
                            }))
                            .doOnSuccess(resp -> {
                                int prompt = resp.usage() != null ? resp.usage().promptTokens() : 0;
                                int completion = resp.usage() != null ? resp.usage().completionTokens() : 0;
                                observer.recordSuccess(sample, new AuditEvent(traceId, tenant, appKey, provider, model, false,
                                        prompt, completion, System.currentTimeMillis() - startMs, "ok", false,
                                        System.currentTimeMillis()));
                            })
                            .doOnError(e -> {
                                String type = (e instanceof GatewayException ge) ? ge.getType() : "internal_error";
                                observer.recordError(sample, new AuditEvent(traceId, tenant, appKey, provider, model, false,
                                        0, 0, System.currentTimeMillis() - startMs, type, false, System.currentTimeMillis()), type);
                            });
                });
    }

    private Mono<ChatResponse> cachedOrCall(List<RouteTarget> candidates, ChatRequest request,
                                            String tenant, String appKey, String model, long estimate) {
        // 1) 精确缓存（仅 temperature=0 确定性输出）
        if (exactCache.isCacheable(request)) {
            String key = exactCache.key(request, model);
            ChatResponse cached = exactCache.get(key);
            if (cached != null) {
                log.info("exact cache hit, key={}", key);
                observer.recordCache("exact", true);
                return settle(tenant, appKey, -estimate).thenReturn(cached);
            }
            observer.recordCache("exact", false);
            return failoverMono(candidates, request)
                    .flatMap(resp -> {
                        exactCache.put(key, resp);
                        return settleUsage(resp, estimate, tenant, appKey);
                    });
        }
        // 2) 语义缓存（可选，非流式；失败自动回退到直接调模型，不影响主链路）
        if (semanticCache.isPresent() && semanticCache.get().isEnabled()) {
            SemanticCacheService sc = semanticCache.get();
            String query = lastUserContent(request);
            return sc.find(query, model)
                    .onErrorResume(e -> {
                        log.warn("semantic cache find failed, fallback to provider: {}", e.getMessage());
                        return Mono.just(Optional.empty());
                    })
                    .flatMap(hit -> {
                        if (hit.isPresent()) {
                            log.info("semantic cache hit, model={}", model);
                            observer.recordCache("semantic", true);
                            return settle(tenant, appKey, -estimate).thenReturn(hit.get());
                        }
                        observer.recordCache("semantic", false);
                        return failoverMono(candidates, request)
                                .flatMap(resp -> sc.put(query, model, resp)
                                        .onErrorResume(e -> Mono.empty())
                                        .then(settleUsage(resp, estimate, tenant, appKey)));
                    });
        }
        // 3) 无缓存
        return failoverMono(candidates, request)
                .flatMap(resp -> settleUsage(resp, estimate, tenant, appKey));
    }

    // ===== 流式 =====

    public Flux<String> stream(ChatRequest request, String routeTag, String tenant, String appKey) {
        String traceId = UUID.randomUUID().toString();
        Timer.Sample sample = observer.start();
        long startMs = System.currentTimeMillis();

        return resolveCandidates(routeTag, tenant, appKey)
                .flatMapMany(candidates -> {
                    if (candidates.isEmpty()) {
                        return Flux.error(new GatewayException(HttpStatus.BAD_REQUEST, "no_route", "no candidates"));
                    }
                    String provider = candidates.get(0).provider();
                    String model = candidates.get(0).model();
                    return rateLimitCheck(candidates, tenant, appKey)
                            .then(moderationCheck(request))
                            .thenMany(Flux.defer(() -> {
                                long estimate = TokenEstimator.estimate(request, properties.getBudget().getDefaultCompletionTokens());
                                return reserve(tenant, appKey, estimate)
                                        .flatMapMany(reserved -> reserved
                                                ? streamWithSettle(candidates, request, tenant, appKey, estimate)
                                                : Flux.error(budgetExceeded()));
                            }))
                            .doOnCancel(() -> {
                                log.info("client disconnected, upstream stream cancelled");
                                observer.recordDisconnect();
                            })
                            .doFinally(sig -> {
                                if (sig == SignalType.CANCEL) {
                                    return;
                                }
                                String status = (sig == SignalType.ON_ERROR) ? "error" : "ok";
                                String type = (sig == SignalType.ON_ERROR) ? "stream_error" : "ok";
                                observer.recordStream(sample, new AuditEvent(traceId, tenant, appKey, provider, model, true,
                                        0, 0, System.currentTimeMillis() - startMs, type, false, System.currentTimeMillis()), status);
                            });
                });
    }

    private Flux<String> streamWithSettle(List<RouteTarget> candidates, ChatRequest request,
                                          String tenant, String appKey, long estimate) {
        AtomicLong actual = new AtomicLong(estimate);
        return streamChunks(candidates, 0, request)
                .doOnNext(chunk -> {
                    if (chunk.usage() != null) {
                        actual.set(chunk.usage().totalTokens());
                    }
                })
                .map(this::formatChunk)
                .concatWith(Mono.just(DONE_FRAME))
                .doFinally(sig -> {
                    long delta = (sig == SignalType.ON_ERROR)
                            ? -estimate                       // 失败退款
                            : actual.get() - estimate;        // 正常/取消按实际结算
                    settle(tenant, appKey, delta)
                            .subscribe(v -> {
                            }, e -> log.warn("stream budget settle failed", e));
                    observer.recordStreamTokens(Math.max(0, actual.get()));
                });
    }

    // ===== 故障转移 =====

    private Mono<ChatResponse> failoverMono(List<RouteTarget> candidates, ChatRequest request) {
        return Flux.fromIterable(candidates)
                .concatMap(target -> tryMono(target, request).onErrorResume(e -> Mono.empty()))
                .next()
                .switchIfEmpty(Mono.error(new GatewayException(HttpStatus.BAD_GATEWAY,
                        "upstream_error", "all candidate models failed")));
    }

    private Mono<ChatResponse> tryMono(RouteTarget target, ChatRequest request) {
        ModelProvider provider = registry.get(target.provider());
        request.setModel(target.model());
        String key = target.provider() + ":" + target.model();
        return resilienceManager.decorateMono(key, provider.chat(request));
    }

    private Flux<ChatChunk> streamChunks(List<RouteTarget> candidates, int idx, ChatRequest request) {
        if (idx >= candidates.size()) {
            return Flux.error(new GatewayException(HttpStatus.BAD_GATEWAY,
                    "upstream_error", "all candidate models failed"));
        }
        RouteTarget target = candidates.get(idx);
        ModelProvider provider = registry.get(target.provider());
        request.setModel(target.model());
        String key = target.provider() + ":" + target.model();
        AtomicBoolean emitted = new AtomicBoolean(false);
        return resilienceManager.decorateFlux(key, provider.chatStream(request))
                .onBackpressureBuffer(properties.getSse().getBufferSize(), BufferOverflowStrategy.DROP_OLDEST)
                .timeout(Duration.ofMillis(properties.getSse().getIdleTimeoutMs()))
                .doOnNext(chunk -> emitted.set(true))
                .onErrorResume(e -> {
                    if (emitted.get()) {
                        return Flux.error(e);
                    }
                    log.warn("stream candidate {} failed before first byte, failing over", key);
                    return streamChunks(candidates, idx + 1, request);
                });
    }

    // ===== 路由 / 限流 / 风控 / 预算 =====

    private Mono<List<RouteTarget>> resolveCandidates(String routeTag, String tenant, String appKey) {
        // 手动降级开关优先
        if (properties.getRouting().isDowngradeEnabled()) {
            return Mono.just(router.resolveCandidates(routeTag, true));
        }
        // 预算触发降级：剩余预算低于阈值时按价格升序（廉价优先）
        if (properties.getBudget().isEnabled()) {
            long total = properties.getBudget().getTotalLimit();
            long threshold = (long) (total * properties.getBudget().getDowngradeThresholdRatio());
            return budgetService.remaining(tenant, appKey)
                    .map(remaining -> router.resolveCandidates(routeTag, remaining < threshold));
        }
        return Mono.just(router.resolveCandidates(routeTag, false));
    }

    private Mono<Void> rateLimitCheck(List<RouteTarget> candidates, String tenant, String appKey) {
        if (!properties.getRateLimit().isEnabled() || candidates.isEmpty()) {
            return Mono.empty();
        }
        return rateLimiter.tryAcquire(tenant, appKey, candidates.get(0).model())
                .flatMap(allowed -> allowed
                        ? Mono.empty()
                        : Mono.error(new GatewayException(HttpStatus.TOO_MANY_REQUESTS,
                                "rate_limit_error", "rate limit exceeded")));
    }

    private Mono<Void> moderationCheck(ChatRequest request) {
        Optional<String> violation = moderationService.detect(lastUserContent(request));
        if (violation.isPresent()) {
            return Mono.error(new GatewayException(HttpStatus.BAD_REQUEST,
                    "content_policy_violation", violation.get()));
        }
        return Mono.empty();
    }

    private Mono<Boolean> reserve(String tenant, String appKey, long tokens) {
        if (!properties.getBudget().isEnabled()) {
            return Mono.just(true);
        }
        return budgetService.reserve(tenant, appKey, tokens);
    }

    private Mono<Void> settle(String tenant, String appKey, long delta) {
        if (!properties.getBudget().isEnabled()) {
            return Mono.empty();
        }
        return budgetService.settle(tenant, appKey, delta)
                .doOnSuccess(v -> budgetWarning.check(tenant, appKey));
    }

    private Mono<ChatResponse> settleUsage(ChatResponse resp, long estimate, String tenant, String appKey) {
        long actual = resp.usage() != null ? resp.usage().totalTokens() : estimate;
        return settle(tenant, appKey, actual - estimate).thenReturn(resp);
    }

    private Mono<ChatResponse> refund(String tenant, String appKey, long estimate, Throwable error) {
        return settle(tenant, appKey, -estimate).then(Mono.error(error));
    }

    private GatewayException budgetExceeded() {
        return new GatewayException(HttpStatus.PAYMENT_REQUIRED, "insufficient_quota", "budget quota exceeded");
    }

    // ===== 辅助 =====

    private String lastUserContent(ChatRequest request) {
        if (request.getMessages() == null) {
            return "";
        }
        for (int i = request.getMessages().size() - 1; i >= 0; i--) {
            var m = request.getMessages().get(i);
            if ("user".equals(m.role()) && m.content() != null) {
                return m.content();
            }
        }
        return "";
    }

    private String formatChunk(ChatChunk chunk) {
        try {
            return "data: " + objectMapper.writeValueAsString(chunk) + "\n\n";
        } catch (JsonProcessingException e) {
            throw new GatewayException(HttpStatus.INTERNAL_SERVER_ERROR, "serialize_error",
                    "failed to serialize chunk");
        }
    }
}
