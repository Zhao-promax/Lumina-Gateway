package com.lumina.gateway.auth;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求 nonce 防重放存储。
 * <p>
 * 阶段 1 使用内存实现（单实例有效）；阶段 3 接入 Redis 后替换为分布式实现，支持多实例防重放。
 */
@Component
public class NonceStore {

    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();

    /**
     * 若 nonce 不存在则写入并返回 true；已存在返回 false。
     */
    public boolean putIfAbsent(String nonce, long ttlMillis) {
        long expireAt = System.currentTimeMillis() + ttlMillis;
        return store.putIfAbsent(nonce, expireAt) == null;
    }

    /**
     * 清理过期 nonce，避免内存无限增长。
     */
    public void evict() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue() < now);
    }
}
