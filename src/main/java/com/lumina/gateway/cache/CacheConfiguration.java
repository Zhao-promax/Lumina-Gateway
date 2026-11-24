package com.lumina.gateway.cache;

import com.lumina.gateway.cache.embedding.EmbeddingProvider;
import com.lumina.gateway.cache.embedding.OpenAiCompatibleEmbeddingProvider;
import com.lumina.gateway.config.GatewayProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 语义缓存装配：Embedding 提供方 + 向量存储后端（内存 / RediSearch）。
 */
@Configuration
public class CacheConfiguration {

    @Bean
    @ConditionalOnProperty(name = "gateway.cache.semantic-enabled", havingValue = "true")
    public EmbeddingProvider embeddingProvider(GatewayProperties properties) {
        GatewayProperties.Embedding e = properties.getCache().getEmbedding();
        return new OpenAiCompatibleEmbeddingProvider(e.getBaseUrl(), e.getApiKey(), e.getModel(), e.getDimension());
    }

    @Bean
    @ConditionalOnProperty(name = "gateway.cache.semantic-enabled", havingValue = "true")
    public VectorStore vectorStore(GatewayProperties properties, ObjectProvider<StringRedisTemplate> redis) {
        if ("redis-search".equals(properties.getCache().getVectorBackend())) {
            StringRedisTemplate template = redis.getIfAvailable();
            if (template != null) {
                return new RedisSearchVectorStore(template, "semantic_idx",
                        properties.getCache().getEmbedding().getDimension(), "sem:");
            }
        }
        return new InMemoryVectorStore();
    }
}
