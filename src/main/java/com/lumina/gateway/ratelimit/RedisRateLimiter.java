package com.lumina.gateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Redis 令牌桶限流（分布式实现，Lua 原子）。
 * <p>
 * Redis 异常时「失败放行」（onErrorReturn true）避免因限流后端故障导致网关整体不可用；
 * 代价是 Redis 短暂故障期间限流失效，生产可结合监控告警。
 */
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local requested = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])
            local bucket = redis.call('HMGET', key, 'tokens', 'last')
            local tokens = tonumber(bucket[1])
            local last = tonumber(bucket[2])
            if tokens == nil then
                tokens = capacity
                last = now
            end
            local refill = (now - last) / 1000 * rate
            tokens = math.min(capacity, tokens + refill)
            local ttl = math.ceil(capacity / rate * 1000) * 2
            if tokens >= requested then
                tokens = tokens - requested
                redis.call('HSET', key, 'tokens', tokens, 'last', now)
                redis.call('PEXPIRE', key, ttl)
                return 1
            end
            redis.call('HSET', key, 'tokens', tokens, 'last', now)
            redis.call('PEXPIRE', key, ttl)
            return 0
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private final ReactiveStringRedisTemplate redis;
    private volatile int permitsPerSecond;

    public RedisRateLimiter(ReactiveStringRedisTemplate redis, int permitsPerSecond) {
        this.redis = redis;
        this.permitsPerSecond = permitsPerSecond;
    }

    @Override
    public void updateRate(int permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
    }

    @Override
    public Mono<Boolean> tryAcquire(String tenant, String appKey, String model) {
        String key = "rate:" + tenant + ":" + appKey + ":" + model;
        long now = System.currentTimeMillis();
        return redis.execute(SCRIPT, List.of(key),
                        List.of(String.valueOf(permitsPerSecond), String.valueOf(permitsPerSecond),
                                "1", String.valueOf(now)))
                .next()
                .map(r -> r != null && r == 1L)
                .onErrorResume(e -> {
                    log.warn("redis rate limiter unavailable, fail-open: {}", e.getMessage());
                    return Mono.just(true);
                });
    }
}
