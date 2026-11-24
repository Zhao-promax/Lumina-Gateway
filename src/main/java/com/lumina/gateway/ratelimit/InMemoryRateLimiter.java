package com.lumina.gateway.ratelimit;

import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存令牌桶限流（单实例兜底实现）。
 * <p>
 * 分布式多实例场景请切换 {@code gateway.rate-limit.backend=redis} 使用 {@link RedisRateLimiter}。
 */
public class InMemoryRateLimiter implements RateLimiter {

    private volatile int permitsPerSecond;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(int permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
    }

    @Override
    public void updateRate(int permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
        this.buckets.clear();  // 清空旧桶，让后续请求按新速率计数
    }

    @Override
    public Mono<Boolean> tryAcquire(String tenant, String appKey, String model) {
        String key = tenant + ":" + appKey + ":" + model;
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(permitsPerSecond));
        return Mono.just(bucket.tryAcquire());
    }

    private static final class TokenBucket {
        private final double capacity;
        private final double refillPerMs;
        private double tokens;
        private long lastRefillMs;

        TokenBucket(int permitsPerSecond) {
            this.capacity = permitsPerSecond;
            this.refillPerMs = permitsPerSecond / 1000.0;
            this.tokens = permitsPerSecond;
            this.lastRefillMs = System.currentTimeMillis();
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            tokens = Math.min(capacity, tokens + (now - lastRefillMs) * refillPerMs);
            lastRefillMs = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }
}
