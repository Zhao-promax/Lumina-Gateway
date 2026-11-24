package com.lumina.gateway.cache;

import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量存储（单实例、无外部依赖）。余弦相似度暴力检索。
 */
public class InMemoryVectorStore implements VectorStore {

    private static final int MAX_ENTRIES = 5000;

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> put(String key, float[] vector, String value) {
        if (store.size() < MAX_ENTRIES) {
            store.put(key, new Entry(vector, value));
        }
        return Mono.empty();
    }

    @Override
    public Mono<Optional<String>> find(String model, float[] queryVector, double threshold) {
        String prefix = model + ":";
        String bestKey = null;
        double bestSimilarity = -1;
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
        if (bestKey != null && bestSimilarity >= threshold) {
            return Mono.just(Optional.ofNullable(store.get(bestKey).value));
        }
        return Mono.just(Optional.empty());
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

    private record Entry(float[] vector, String value) {
    }
}
