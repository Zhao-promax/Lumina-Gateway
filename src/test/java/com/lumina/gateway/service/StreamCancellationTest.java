package com.lumina.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.api.dto.ChatChunk;
import com.lumina.gateway.api.dto.ChatMessage;
import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.api.dto.ChunkChoice;
import com.lumina.gateway.api.dto.ChunkDelta;
import com.lumina.gateway.audit.AuditService;
import com.lumina.gateway.budget.BudgetWarningService;
import com.lumina.gateway.budget.InMemoryBudgetService;
import com.lumina.gateway.cache.ExactCacheService;
import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.metrics.GatewayMetrics;
import com.lumina.gateway.metrics.GatewayObserver;
import com.lumina.gateway.moderation.ContentModerationService;
import com.lumina.gateway.provider.ProviderRegistry;
import com.lumina.gateway.provider.spi.ModelProvider;
import com.lumina.gateway.ratelimit.InMemoryRateLimiter;
import com.lumina.gateway.resilience.ResilienceManager;
import com.lumina.gateway.routing.RouteResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验「客户端断连 → 下游请求取消」的传播链路。
 * <p>
 * 模拟客户端订阅流后中途 dispose（断连），断言下游 provider 的流被 cancel。
 */
class StreamCancellationTest {

    @Test
    void cancelsDownstreamStreamWhenClientDisconnects() throws Exception {
        AtomicBoolean downstreamCancelled = new AtomicBoolean(false);
        ChatService service = serviceWith(() -> Flux.interval(Duration.ofMillis(20))
                .map(i -> new ChatChunk("id", "chat.completion.chunk", 0L, "mock-model",
                        List.of(new ChunkChoice(0, new ChunkDelta(null, "token-" + i), null)), null))
                .doOnCancel(() -> downstreamCancelled.set(true)));

        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(new ChatMessage("user", "hi", null)));

        CountDownLatch firstChunk = new CountDownLatch(1);
        Disposable subscription = service.stream(request, null, "t", "app")
                .subscribe(s -> firstChunk.countDown());

        assertThat(firstChunk.await(3, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();  // 模拟客户端断连

        // 取消应沿 Reactor 链传播到下游 provider
        assertThat(downstreamCancelled.get()).isTrue();
    }

    private ChatService serviceWith(java.util.function.Supplier<Flux<ChatChunk>> streamSupplier) {
        GatewayProperties props = new GatewayProperties();
        props.getResilience().getRetry().setMaxAttempts(1);
        props.getRateLimit().setEnabled(false);
        props.getBudget().setEnabled(false);
        props.getModeration().setEnabled(false);

        GatewayProperties.Routing routing = new GatewayProperties.Routing();
        routing.setDefaultTag("default");
        GatewayProperties.Route route = new GatewayProperties.Route();
        route.setTag("default");
        GatewayProperties.ModelRef ref = new GatewayProperties.ModelRef();
        ref.setProvider("mock");
        ref.setModel("mock-model");
        route.setModels(List.of(ref));
        routing.setRoutes(List.of(route));
        props.setRouting(routing);

        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new StubProvider(streamSupplier));

        GatewayMetrics metrics = new GatewayMetrics(new SimpleMeterRegistry());
        InMemoryBudgetService budgetService = new InMemoryBudgetService(Long.MAX_VALUE, Long.MAX_VALUE);
        RouteResolver resolver = new RouteResolver(props);
        ResilienceManager resilience = new ResilienceManager(props, metrics);
        GatewayObserver observer = new GatewayObserver(metrics,
                new AuditService(props, Optional.empty(), new ObjectMapper()));
        BudgetWarningService budgetWarning = new BudgetWarningService(budgetService, props, metrics);

        return new ChatService(registry, resolver, resilience, props,
                new InMemoryRateLimiter(1000),
                budgetService,
                budgetWarning,
                new ExactCacheService(props),
                Optional.empty(),
                observer,
                new ContentModerationService(props),
                new ObjectMapper());
    }

    static class StubProvider implements ModelProvider {
        private final java.util.function.Supplier<Flux<ChatChunk>> streamSupplier;

        StubProvider(java.util.function.Supplier<Flux<ChatChunk>> streamSupplier) {
            this.streamSupplier = streamSupplier;
        }

        @Override
        public String provider() {
            return "mock";
        }

        @Override
        public boolean supports(String model) {
            return true;
        }

        @Override
        public Mono<ChatResponse> chat(ChatRequest request) {
            return Mono.empty();
        }

        @Override
        public Flux<ChatChunk> chatStream(ChatRequest request) {
            return streamSupplier.get();
        }
    }
}
