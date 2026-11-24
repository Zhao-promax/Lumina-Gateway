package com.lumina.gateway.budget;

import com.lumina.gateway.config.GatewayProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * 预算后端选择：backend=redis 使用分布式 Redis 实现，否则内存实现。
 */
@Configuration
public class BudgetConfiguration {

    @Bean
    @ConditionalOnProperty(name = "gateway.budget.backend", havingValue = "redis")
    public BudgetService redisBudgetService(ReactiveStringRedisTemplate redis, GatewayProperties properties) {
        return new RedisBudgetService(redis,
                properties.getBudget().getDailyLimit(),
                properties.getBudget().getTotalLimit());
    }

    @Bean
    @ConditionalOnMissingBean(BudgetService.class)
    public BudgetService inMemoryBudgetService(GatewayProperties properties) {
        return new InMemoryBudgetService(
                properties.getBudget().getDailyLimit(),
                properties.getBudget().getTotalLimit());
    }
}
