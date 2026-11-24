package com.lumina.gateway.budget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

/**
 * Redis 预算实现（Lua 原子预扣/结算，杜绝并发竞态）。
 * <p>
 * Redis 异常时「失败关闭」（reserve 返回 false）避免预算超支。
 */
public class RedisBudgetService implements BudgetService {

    private static final Logger log = LoggerFactory.getLogger(RedisBudgetService.class);

    private static final String RESERVE_LUA = """
            local dailyKey = KEYS[1]
            local totalKey = KEYS[2]
            local dailyLimit = tonumber(ARGV[1])
            local totalLimit = tonumber(ARGV[2])
            local tokens = tonumber(ARGV[3])
            local dailyUsed = tonumber(redis.call('GET', dailyKey) or '0')
            local totalUsed = tonumber(redis.call('GET', totalKey) or '0')
            if dailyUsed + tokens > dailyLimit or totalUsed + tokens > totalLimit then
                return 0
            end
            redis.call('INCRBY', dailyKey, tokens)
            redis.call('INCRBY', totalKey, tokens)
            redis.call('EXPIRE', dailyKey, 86400)
            return 1
            """;

    private static final String SETTLE_LUA = """
            local dailyKey = KEYS[1]
            local totalKey = KEYS[2]
            local delta = tonumber(ARGV[1])
            local dailyUsed = math.max(0, tonumber(redis.call('GET', dailyKey) or '0') + delta)
            local totalUsed = math.max(0, tonumber(redis.call('GET', totalKey) or '0') + delta)
            redis.call('SET', dailyKey, dailyUsed, 'EX', 86400)
            redis.call('SET', totalKey, totalUsed)
            return 1
            """;

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>(RESERVE_LUA, Long.class);
    private static final DefaultRedisScript<Long> SETTLE_SCRIPT = new DefaultRedisScript<>(SETTLE_LUA, Long.class);

    private final ReactiveStringRedisTemplate redis;
    private volatile long dailyLimit;
    private volatile long totalLimit;

    public RedisBudgetService(ReactiveStringRedisTemplate redis, long dailyLimit, long totalLimit) {
        this.redis = redis;
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
        List<String> keys = List.of(
                "budget:daily:" + tenant + ":" + appKey + ":" + LocalDate.now(),
                "budget:total:" + tenant + ":" + appKey);
        return redis.execute(RESERVE_SCRIPT, keys,
                        List.of(String.valueOf(dailyLimit), String.valueOf(totalLimit), String.valueOf(tokens)))
                .next()
                .map(r -> r != null && r == 1L)
                .onErrorResume(e -> {
                    log.error("redis budget reserve failed, fail-closed: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Void> settle(String tenant, String appKey, long delta) {
        List<String> keys = List.of(
                "budget:daily:" + tenant + ":" + appKey + ":" + LocalDate.now(),
                "budget:total:" + tenant + ":" + appKey);
        return redis.execute(SETTLE_SCRIPT, keys, List.of(String.valueOf(delta)))
                .next()
                .onErrorResume(e -> {
                    log.error("redis budget settle failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    @Override
    public Mono<Long> remaining(String tenant, String appKey) {
        return redis.opsForValue().get("budget:total:" + tenant + ":" + appKey)
                .map(v -> Math.max(0, totalLimit - Long.parseLong(v)))
                .defaultIfEmpty(totalLimit);
    }
}
