package com.lumina.gateway.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.api.dto.ErrorBody;
import com.lumina.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * AppKey 签名鉴权过滤器。
 * <p>
 * 校验请求头：X-App-Key、X-Timestamp、X-Nonce、X-Signature。
 * 签名串由 {@link SignatureService} 定义，校验时间戳窗口与 nonce 防重放。
 */
@Component
public class AppKeyAuthFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AppKeyAuthFilter.class);

    private static final String HEADER_APP_KEY = "X-App-Key";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_NONCE = "X-Nonce";
    private static final String HEADER_SIGNATURE = "X-Signature";

    private final GatewayProperties properties;
    private final SignatureService signatureService;
    private final NonceStore nonceStore;
    private final ObjectMapper objectMapper;

    public AppKeyAuthFilter(GatewayProperties properties,
                            SignatureService signatureService,
                            NonceStore nonceStore,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.signatureService = signatureService;
        this.nonceStore = nonceStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.getAuth().isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (!path.startsWith("/v1/")) {
            return chain.filter(exchange);
        }

        String appKey = request.getHeaders().getFirst(HEADER_APP_KEY);
        String timestamp = request.getHeaders().getFirst(HEADER_TIMESTAMP);
        String nonce = request.getHeaders().getFirst(HEADER_NONCE);
        String signature = request.getHeaders().getFirst(HEADER_SIGNATURE);

        if (isBlank(appKey) || isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            return unauthorized(exchange, "missing_auth_headers", "missing required auth headers");
        }

        GatewayProperties.App app = findApp(appKey);
        if (app == null) {
            return unauthorized(exchange, "invalid_app_key", "invalid app key");
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return unauthorized(exchange, "invalid_timestamp", "invalid timestamp");
        }

        // 约定 X-Timestamp 为 Unix 秒级时间戳
        long windowSeconds = properties.getAuth().getTimestampWindowSeconds();
        if (Math.abs(System.currentTimeMillis() / 1000 - ts) > windowSeconds) {
            return unauthorized(exchange, "timestamp_expired", "timestamp out of window");
        }

        if (!nonceStore.putIfAbsent(nonce, windowSeconds * 2 * 1000)) {
            return unauthorized(exchange, "nonce_replayed", "nonce already used");
        }

        String method = request.getMethod() != null ? request.getMethod().name() : "";
        String expected = signatureService.sign(method, path, timestamp, nonce, app.getAppSecret());
        if (!signatureService.verify(expected, signature)) {
            return unauthorized(exchange, "invalid_signature", "invalid signature");
        }

        return chain.filter(exchange);
    }

    private GatewayProperties.App findApp(String appKey) {
        return properties.getAuth().getApps().stream()
                .filter(a -> a.getAppKey().equals(appKey))
                .findFirst()
                .orElse(null);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String type, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ErrorBody body = new ErrorBody(new ErrorBody.ErrorDetail(message, type, null, null));
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":{\"message\":\"unauthorized\"}}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
