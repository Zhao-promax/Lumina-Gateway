package com.lumina.gateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.api.dto.ChatChunk;
import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.exception.GatewayException;
import com.lumina.gateway.provider.spi.ModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI 兼容协议适配器基类。
 * <p>
 * DeepSeek、通义千问（compatible-mode）均提供 OpenAI 兼容接口，因此共用同一套 HTTP 调用逻辑，
 * 仅 baseUrl / apiKey / provider 标识不同；差异由子类继承体现，未来若某厂商出现非兼容协议，
 * 可覆盖对应方法或独立实现 {@link ModelProvider}。
 */
public abstract class OpenAiCompatibleProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final String provider;
    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    protected OpenAiCompatibleProvider(String provider, String baseUrl, String apiKey, ObjectMapper objectMapper) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.webClient = WebClient.create(baseUrl);
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public boolean supports(String model) {
        return true;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        ensureApiKey();
        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildBody(request))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(body -> Mono.error(mapError(resp.statusCode(), body))))
                .bodyToMono(ChatResponse.class);
    }

    @Override
    public Flux<ChatChunk> chatStream(ChatRequest request) {
        ensureApiKey();
        Map<String, Object> body = buildBody(request);
        body.put("stream", true);
        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(err -> Mono.error(mapError(resp.statusCode(), err))))
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .map(ServerSentEvent::data)
                .filter(data -> data != null && !data.isBlank())
                .takeUntil(this::isDone)
                .filter(data -> !isDone(data))
                .map(this::parseChunk)
                .filter(Objects::nonNull)
                .doOnCancel(() -> log.info("provider={} downstream stream cancelled", provider));
    }

    protected Map<String, Object> buildBody(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("messages", request.getMessages());
        if (request.getStream() != null) {
            body.put("stream", request.getStream());
        }
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        body.putAll(request.getExtra());
        return body;
    }

    private boolean isDone(String data) {
        return "[DONE]".equals(data.trim());
    }

    private ChatChunk parseChunk(String data) {
        try {
            return objectMapper.readValue(data, ChatChunk.class);
        } catch (Exception e) {
            // 脏分片捕获与丢弃，保证后续分片正常
            log.warn("provider={} dropping dirty chunk: {}", provider, data);
            return null;
        }
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "provider_not_configured",
                    "provider " + provider + " has no api-key configured (set env DEEPSEEK_API_KEY / QWEN_API_KEY)");
        }
    }

    private GatewayException mapError(HttpStatusCode status, String body) {
        int code = status.value();
        String type = switch (code) {
            case 400, 422 -> "invalid_request_error";
            case 401, 403 -> "authentication_error";
            case 429 -> "rate_limit_error";
            default -> "upstream_error";
        };
        return new GatewayException(toHttpStatus(code), type,
                "upstream " + provider + " error: " + truncate(body));
    }

    private HttpStatus toHttpStatus(int code) {
        try {
            return HttpStatus.valueOf(code);
        } catch (IllegalArgumentException e) {
            return HttpStatus.BAD_GATEWAY;
        }
    }

    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) : body;
    }
}
