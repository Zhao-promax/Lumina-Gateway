package com.lumina.gateway.ratelimit;

import reactor.core.publisher.Mono;

/**
 * 多维限流接口：按 租户 / AppKey / 模型 维度限流。
 */
public interface RateLimiter {

    /**
     * 尝试获取一个令牌。
     *
     * @return true 允许，false 拒绝
     */
    Mono<Boolean> tryAcquire(String tenant, String appKey, String model);
}
