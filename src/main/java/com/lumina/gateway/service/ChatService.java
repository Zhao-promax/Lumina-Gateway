package com.lumina.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.api.dto.ChatChunk;
import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.exception.GatewayException;
import com.lumina.gateway.provider.ProviderRegistry;
import com.lumina.gateway.provider.spi.ModelProvider;
import com.lumina.gateway.ratelimit.RateLimiter;
import com.lumina.gateway.resilience.ResilienceManager;
import com.lumina.gateway.routing.RouteResolver;
import com.lumina.gateway.routing.RouteTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网关编排：路由解析 → 限流 → 主备故障转移/降级（含 Resilience4j 熔断/超时/重试）→ 结果标准化。
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
    private final ObjectMapper objectMapper;

    public ChatService(ProviderRegistry registry,
                       RouteResolver router,
                       ResilienceManager resilienceManager,
                       GatewayProperties properties,
                       RateLimiter rateLimiter,
                       ObjectMapper objectMapper) {
        this.registry = registry;
        this.router = router;
        this.resilienceManager = resilienceManager;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    public Mono<ChatResponse> complete(ChatRequest request, String routeTag, String tenant, String appKey) {
        List<RouteTarget> candidates = router.resolveCandidates(routeTag);
        return rateLimitCheck(candidates, tenant, appKey)
                .then(failoverMono(candidates, request));
    }

    public Flux<String> stream(ChatRequest request, String routeTag, String tenant, String appKey) {
        List<RouteTarget> candidates = router.resolveCandidates(routeTag);
        return rateLimitCheck(candidates, tenant, appKey)
                .thenMany(streamChunks(candidates, 0, request))
                .map(this::formatChunk)
                .concatWith(Mono.just(DONE_FRAME))
                .doOnCancel(() -> log.info("client disconnected, upstream stream cancelled"));
    }

    private Mono<Void> rateLimitCheck(List<RouteTarget> candidates, String tenant, String appKey) {
        if (!properties.getRateLimit().isEnabled() || candidates.isEmpty()) {
            return Mono.empty();
        }
        RouteTarget target = candidates.get(0);
        return rateLimiter.tryAcquire(tenant, appKey, target.model())
                .flatMap(allowed -> allowed
                        ? Mono.empty()
                        : Mono.error(new GatewayException(HttpStatus.TOO_MANY_REQUESTS,
                                "rate_limit_error", "rate limit exceeded")));
    }

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
                .doOnNext(chunk -> emitted.set(true))
                .onErrorResume(e -> {
                    if (emitted.get()) {
                        // 已产生首字节，不能切换候选，避免输出错乱与重复计费
                        return Flux.error(e);
                    }
                    log.warn("stream candidate {} failed before first byte, failing over", key);
                    return streamChunks(candidates, idx + 1, request);
                });
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
