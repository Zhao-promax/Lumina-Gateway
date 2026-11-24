package com.lumina.gateway.cache;

import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.cache.embedding.EmbeddingProvider;
import com.lumina.gateway.config.GatewayProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding 语义向量缓存（仅非流式请求）。
 * <p>
 * 将用户问题向量化，与历史缓存做余弦相似度匹配，相似度 ≥ 阈值则返回历史答案。
 * 阶段 3 使用内存向量存储 + 余弦检索；生产可替换为 RedisSearch（Redis Stack）KNN 检索。
 * 需要注入 {@link EmbeddingProvider}，否则自动禁用。
 */
@Component
@ConditionalOnProperty(name = "gateway.cache.semantic-enabled", havingValue = "true")
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    private static final int MAX_ENTRIES = 5000;

    private final GatewayProperties properties;
    private final ObjectProvider<EmbeddingProvider> embeddingProviderProvider;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    private volatile EmbeddingProvider embeddingProvider;

    public SemanticCacheService(GatewayProperties properties,
                                ObjectProvider<EmbeddingProvider> embeddingProviderProvider) {
        this.properties = properties;
        this.embeddingProviderProvider = embeddingProviderProvider;
    }

    @PostConstruct
    void init() {
        this.embeddingProvider = embeddingProviderProvider.getIfAvailable();
        if (this.embeddingProvider == null) {
            log.warn("no EmbeddingProvider bean found, semantic cache disabled");
        }
    }

    public boolean isEnabled() {
        return embeddingProvider != null;
    }

    /**
     * 相似度检索，返回最匹配的历史答案（≥ 阈值）。
     */
    public Optional<ChatResponse> find(String query, String model) {
        if (embeddingProvider == null) {
            return Optional.empty();
        }
        float[] queryVector = embeddingProvider.embed(query);
        String bestKey = null;
        double bestSimilarity = -1;
        String prefix = model + ":";
        for (var e : store.entrySet()) {
            if (!e.getKey().startsWith(prefix)) {
                continue;
            }
            double similarity = cosine(queryVector, e.getValue().vector);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestKey = e.getKey();
            }
        }
        if (bestKey != null && bestSimilarity >= properties.getCache().getSemanticThreshold()) {
            return Optional.ofNullable(store.get(bestKey).response);
        }
        return Optional.empty();
    }

    /**
     * 存储问答的向量与响应。
     */
    public void put(String query, String model, ChatResponse response) {
        if (embeddingProvider == null || store.size() >= MAX_ENTRIES) {
            return;
        }
        float[] vector = embeddingProvider.embed(query);
        store.put(model + ":" + UUID.randomUUID(), new Entry(vector, response));
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record Entry(float[] vector, ChatResponse response) {
    }
}
