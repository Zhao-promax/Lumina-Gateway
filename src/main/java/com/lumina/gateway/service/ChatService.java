package com.lumina.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.api.dto.ChatChunk;
import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.exception.GatewayException;
import com.lumina.gateway.provider.ProviderRegistry;
import com.lumina.gateway.provider.spi.ModelProvider;
import com.lumina.gateway.routing.LabelRouter;
import com.lumina.gateway.routing.RouteTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 网关编排：路由解析 → 适配器调用 → 结果标准化。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String DONE_FRAME = "data: [DONE]\n\n";

    private final ProviderRegistry registry;
    private final LabelRouter router;
    private final ObjectMapper objectMapper;

    public ChatService(ProviderRegistry registry, LabelRouter router, ObjectMapper objectMapper) {
        this.registry = registry;
        this.router = router;
        this.objectMapper = objectMapper;
    }

    public Mono<ChatResponse> complete(ChatRequest request, String routeTag) {
        RouteTarget target = router.resolve(routeTag);
        ModelProvider provider = registry.get(target.provider());
        request.setModel(target.model());
        return provider.chat(request);
    }

    public Flux<String> stream(ChatRequest request, String routeTag) {
        RouteTarget target = router.resolve(routeTag);
        ModelProvider provider = registry.get(target.provider());
        request.setModel(target.model());
        return provider.chatStream(request)
                .map(this::formatChunk)
                .concatWith(Mono.just(DONE_FRAME))
                .doOnCancel(() -> log.info("client disconnected, upstream stream cancelled"));
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
