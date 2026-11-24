package com.lumina.gateway.cache;

import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * 向量存储抽象：存放向量 + 值，支持按向量相似度检索。
 * <p>
 * 实现：内存余弦（{@link InMemoryVectorStore}）与 RediSearch KNN（{@link RedisSearchVectorStore}）。
 */
public interface VectorStore {

    /**
     * 存入向量与值（值通常为响应 JSON 字符串）。
     */
    Mono<Void> put(String key, float[] vector, String value);

    /**
     * 检索与查询向量最相似的值，相似度 ≥ 阈值时返回。
     *
     * @param model 模型维度隔离（key 前缀）
     */
    Mono<Optional<String>> find(String model, float[] queryVector, double threshold);
}
