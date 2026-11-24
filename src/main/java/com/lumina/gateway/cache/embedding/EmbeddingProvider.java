package com.lumina.gateway.cache.embedding;

import reactor.core.publisher.Mono;

/**
 * Embedding 向量生成接口（语义缓存用，可插拔）。
 * <p>
 * 生产可接入低成本 Embedding API 或本地向量模型；未提供实现时语义缓存自动禁用。
 */
public interface EmbeddingProvider {

    /**
     * 将文本转为向量（异步）。
     */
    Mono<float[]> embed(String text);

    /**
     * 向量维度。
     */
    int dimension();
}
