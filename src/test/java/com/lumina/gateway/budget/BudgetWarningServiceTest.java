package com.lumina.gateway.budget;

import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.metrics.GatewayMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetWarningServiceTest {

    @Test
    void recordsWarningWhenBudgetNearlyExhausted() {
        GatewayProperties props = new GatewayProperties();
        props.getBudget().setTotalLimit(100);
        props.getBudget().setWarnRatio(0.8);   // 用量达 80% 告警

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        InMemoryBudgetService budget = new InMemoryBudgetService(100, 100);
        BudgetWarningService warning = new BudgetWarningService(budget, props, metrics);

        budget.reserve("t", "app", 90).block();  // 90/100 = 90% > 80%
        warning.check("t", "app");

        assertThat(registry.get("gateway.budget.warnings").counter().count()).isEqualTo(1.0);
    }

    @Test
    void noWarningWhenBudgetAdequate() {
        GatewayProperties props = new GatewayProperties();
        props.getBudget().setTotalLimit(100);
        props.getBudget().setWarnRatio(0.8);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        InMemoryBudgetService budget = new InMemoryBudgetService(100, 100);
        BudgetWarningService warning = new BudgetWarningService(budget, props, metrics);

        budget.reserve("t", "app", 10).block();  // 10% < 80%
        warning.check("t", "app");

        assertThat(registry.find("gateway.budget.warnings").counter()).isNull();
    }
}
