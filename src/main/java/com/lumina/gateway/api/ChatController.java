package com.lumina.gateway.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.exception.GatewayException;
import com.lumina.gateway.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * OpenAI 兼容入口：/v1/chat/completions，支持 stream=true/false。
 */
@RestController
public class ChatController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/v1/chat/completions")
    public Mono<Void> chat(@Valid @RequestBody ChatRequest request,
                           @RequestHeader(value = "X-Route-Tag", required = false) String routeTag,
                           ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();

        if (Boolean.TRUE.equals(request.getStream())) {
            response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
            Flux<Mono<DataBuffer>> body = chatService.stream(request, routeTag)
                    .map(frame -> Mono.just(response.bufferFactory().wrap(frame.getBytes(StandardCharsets.UTF_8))));
            return response.writeAndFlushWith(body);
        }

        return chatService.complete(request, routeTag)
                .flatMap(resp -> writeJson(response, resp));
    }

    private Mono<Void> writeJson(ServerHttpResponse response, Object value) {
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(value);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            return Mono.error(new GatewayException(HttpStatus.INTERNAL_SERVER_ERROR, "serialize_error", e.getMessage()));
        }
    }
}
