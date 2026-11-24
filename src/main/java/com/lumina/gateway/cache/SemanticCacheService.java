package com.lumina.gateway.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.cache.embedding.EmbeddingProvider;
import com.lumina.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Embedding 语义向量缓存（仅非流式请求，默认关闭）。
 * <p>
 * 将用户问题向量化，交给 {@link VectorStore} 做相似度检索（内存余弦或 RediSearch KNN），
 * 相似度 ≥ 阈值则返回历史答案。需启用 gateway.cache.semantic-enabled 并配置 Embedding。
 */
@Component
@ConditionalOnProperty(name = "gateway.cache.semantic-enabled", havingValue = "true")
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    private final GatewayProperties properties;
    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public SemanticCacheService(GatewayProperties properties,
                                EmbeddingProvider embeddingProvider,
                                VectorStore vectorStore,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return embeddingProvider != null;
    }

    public Mono<Optional<ChatResponse>> find(String query, String model) {
        return embeddingProvider.embed(query)
                .flatMap(vec -> vectorStore.find(model, vec, properties.getCache().getSemanticThreshold()))
                .flatMap(hit -> {
                    if (hit.isEmpty()) {
                        return Mono.just(Optional.empty());
                    }
                    try {
                        return Mono.just(Optional.of(objectMapper.readValue(hit.get(), ChatResponse.class)));
                    } catch (Exception e) {
                        log.warn("cached response parse failed: {}", e.getMessage());
                        return Mono.just(Optional.empty());
                    }
                });
    }

    public Mono<Void> put(String query, String model, ChatResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            return embeddingProvider.embed(query)
                    .flatMap(vec -> vectorStore.put(model + ":" + UUID.randomUUID(), vec, json));
        } catch (Exception e) {
            log.warn("cached response serialize failed: {}", e.getMessage());
            return Mono.empty();
        }
    }
}
