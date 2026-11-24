package com.lumina.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.api.dto.ChatChunk;
import com.lumina.gateway.api.dto.ChatMessage;
import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.api.dto.ChunkChoice;
import com.lumina.gateway.api.dto.ChunkDelta;
import com.lumina.gateway.api.dto.Usage;
import com.lumina.gateway.audit.AuditService;
import com.lumina.gateway.budget.InMemoryBudgetService;
import com.lumina.gateway.cache.ExactCacheService;
import com.lumina.gateway.metrics.GatewayMetrics;
import com.lumina.gateway.metrics.GatewayObserver;
import com.lumina.gateway.moderation.ContentModerationService;
import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.exception.GatewayException;
import com.lumina.gateway.provider.ProviderRegistry;
import com.lumina.gateway.provider.spi.ModelProvider;
import com.lumina.gateway.ratelimit.InMemoryRateLimiter;
import com.lumina.gateway.resilience.ResilienceManager;
import com.lumina.gateway.routing.RouteResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceTest {

    @Test
    void failsOverToBackupWhenPrimaryFails() {
        ChatService service = service();
        ChatResponse resp = service.complete(request(), null, "t", "app").block();

        assertThat(resp).isNotNull();
        assertThat(resp.model()).isEqualTo("qwen-plus");
    }

    @Test
    void streamFailsOverWhenPrimaryFailsBeforeFirstByte() {
        ChatService service = service();
        List<String> frames = service.stream(request(), null, "t", "app").collectList().block();

        assertThat(frames).isNotNull();
        assertThat(frames).anyMatch(f -> f.contains("world"));
        assertThat(frames).last().isEqualTo("data: [DONE]\n\n");
    }

    // ===== 装配 =====

    private ChatService service() {
        GatewayProperties props = new GatewayProperties();
        props.getResilience().getRetry().setMaxAttempts(1);  // 测试不重试
        props.getResilience().getRetry().setWaitMs(1);
        props.getRateLimit().setEnabled(false);
        props.getBudget().setEnabled(false);
        props.getModeration().setEnabled(false);

        GatewayProperties.Routing routing = new GatewayProperties.Routing();
        routing.setDefaultTag("default");
        GatewayProperties.Route route = new GatewayProperties.Route();
        route.setTag("default");
        route.setModels(List.of(
                modelRef("deepseek", "deepseek-chat"),
                modelRef("qwen", "qwen-plus")));
        routing.setRoutes(List.of(route));
        props.setRouting(routing);

        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new StubProvider("deepseek", true, true, "ignored"));
        registry.register(new StubProvider("qwen", false, false, "world"));

        RouteResolver resolver = new RouteResolver(props);
        ResilienceManager resilience = new ResilienceManager(props);
        GatewayObserver observer = new GatewayObserver(
                new GatewayMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                new AuditService(props, java.util.Optional.empty(), new ObjectMapper()));
        return new ChatService(registry, resolver, resilience, props,
                new InMemoryRateLimiter(1000),
                new InMemoryBudgetService(Long.MAX_VALUE, Long.MAX_VALUE),
                new ExactCacheService(props),
                java.util.Optional.empty(),
                observer,
                new ContentModerationService(props),
                new ObjectMapper());
    }

    private ChatRequest request() {
        ChatRequest req = new ChatRequest();
        req.setMessages(List.of(new ChatMessage("user", "hi", null)));
        return req;
    }

    private GatewayProperties.ModelRef modelRef(String provider, String model) {
        GatewayProperties.ModelRef ref = new GatewayProperties.ModelRef();
        ref.setProvider(provider);
        ref.setModel(model);
        return ref;
    }

    static class StubProvider implements ModelProvider {
        private final String name;
        private final boolean failChat;
        private final boolean failStream;
        private final String content;

        StubProvider(String name, boolean failChat, boolean failStream, String content) {
            this.name = name;
            this.failChat = failChat;
            this.failStream = failStream;
            this.content = content;
        }

        @Override
        public String provider() {
            return name;
        }

        @Override
        public boolean supports(String model) {
            return true;
        }

        @Override
        public Mono<ChatResponse> chat(ChatRequest request) {
            if (failChat) {
                return Mono.error(new GatewayException(HttpStatus.BAD_GATEWAY, "upstream_error", "chat boom"));
            }
            return Mono.just(new ChatResponse("id", "chat.completion", 0L, request.getModel(),
                    List.of(), new Usage(1, 1, 2)));
        }

        @Override
        public Flux<ChatChunk> chatStream(ChatRequest request) {
            if (failStream) {
                return Flux.error(new GatewayException(HttpStatus.BAD_GATEWAY, "upstream_error", "stream boom"));
            }
            ChatChunk chunk = new ChatChunk("id", "chat.completion.chunk", 0L, request.getModel(),
                    List.of(new ChunkChoice(0, new ChunkDelta("assistant", content), null)), null);
            return Flux.just(chunk);
        }
    }
}
