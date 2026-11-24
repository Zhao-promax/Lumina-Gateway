package com.lumina.gateway.budget;

import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存预算实现（单实例兜底）。
 * <p>
 * 分布式多实例请切换 {@code gateway.budget.backend=redis} 使用 {@link RedisBudgetService}。
 */
public class InMemoryBudgetService implements BudgetService {

    private volatile long dailyLimit;
    private volatile long totalLimit;
    private final ConcurrentHashMap<String, Long> dailyUsed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> totalUsed = new ConcurrentHashMap<>();

    public InMemoryBudgetService(long dailyLimit, long totalLimit) {
        this.dailyLimit = dailyLimit;
        this.totalLimit = totalLimit;
    }

    @Override
    public void updateLimits(long dailyLimit, long totalLimit) {
        this.dailyLimit = dailyLimit;
        this.totalLimit = totalLimit;
    }

    @Override
    public Mono<Boolean> reserve(String tenant, String appKey, long tokens) {
        String dailyKey = dailyKey(tenant, appKey);
        String totalKey = totalKey(tenant, appKey);
        synchronized (this) {
            long daily = dailyUsed.getOrDefault(dailyKey, 0L);
            long total = totalUsed.getOrDefault(totalKey, 0L);
            if (daily + tokens > dailyLimit || total + tokens > totalLimit) {
                return Mono.just(false);
            }
            dailyUsed.put(dailyKey, daily + tokens);
            totalUsed.put(totalKey, total + tokens);
            return Mono.just(true);
        }
    }

    @Override
    public Mono<Void> settle(String tenant, String appKey, long delta) {
        String dailyKey = dailyKey(tenant, appKey);
        String totalKey = totalKey(tenant, appKey);
        synchronized (this) {
            dailyUsed.compute(dailyKey, (k, v) -> Math.max(0, (v == null ? 0 : v) + delta));
            totalUsed.compute(totalKey, (k, v) -> Math.max(0, (v == null ? 0 : v) + delta));
        }
        return Mono.empty();
    }

    @Override
    public Mono<Long> remaining(String tenant, String appKey) {
        long total = totalUsed.getOrDefault(totalKey(tenant, appKey), 0L);
        return Mono.just(Math.max(0, totalLimit - total));
    }

    private String dailyKey(String tenant, String appKey) {
        return tenant + ":" + appKey + ":" + LocalDate.now();
    }

    private String totalKey(String tenant, String appKey) {
        return tenant + ":" + appKey;
    }
}
