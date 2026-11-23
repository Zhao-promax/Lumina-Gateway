package com.lumina.gateway.ratelimit;

import com.lumina.gateway.config.GatewayProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * 限流后端选择：backend=redis 时使用分布式 Redis 实现，否则使用内存实现。
 */
@Configuration
public class RateLimitConfiguration {

    @Bean
    @ConditionalOnProperty(name = "gateway.rate-limit.backend", havingValue = "redis")
    public RateLimiter redisRateLimiter(ReactiveStringRedisTemplate redis, GatewayProperties properties) {
        return new RedisRateLimiter(redis, properties.getRateLimit().getPermitsPerSecond());
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter inMemoryRateLimiter(GatewayProperties properties) {
        return new InMemoryRateLimiter(properties.getRateLimit().getPermitsPerSecond());
    }
}
