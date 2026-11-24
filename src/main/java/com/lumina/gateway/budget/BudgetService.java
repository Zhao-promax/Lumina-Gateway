package com.lumina.gateway.budget;

import reactor.core.publisher.Mono;

/**
 * 预算配额接口：按 租户 / App 维度管控日预算与总预算。
 * <p>
 * 采用「预扣 + 结算多退少补」：请求前按估算预扣，请求后按真实 usage 结算。
 */
public interface BudgetService {

    /**
     * 预扣 tokens。预算不足返回 false。
     */
    Mono<Boolean> reserve(String tenant, String appKey, long tokens);

    /**
     * 结算差额。delta &gt; 0 追加扣减，delta &lt; 0 退还。
     */
    Mono<Void> settle(String tenant, String appKey, long delta);

    /**
     * 剩余总预算（仅用于观测/预警）。
     */
    Mono<Long> remaining(String tenant, String appKey);
}
