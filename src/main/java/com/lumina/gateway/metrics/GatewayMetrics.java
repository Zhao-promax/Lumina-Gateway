package com.lumina.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 网关指标埋点（Micrometer → Prometheus）。
 * <p>
 * 采集：请求量、P50/P95/P99 延迟、Token 消耗、缓存命中、断连取消、错误率。
 */
@Component
public class GatewayMetrics {

    private final MeterRegistry registry;

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void recordRequest(Timer.Sample sample, String provider, String model, boolean stream, String status) {
        sample.stop(Timer.builder("gateway.request.duration")
                .description("请求延迟")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry));
        Counter.builder("gateway.requests.total")
                .tag("provider", provider == null ? "unknown" : provider)
                .tag("model", model == null ? "unknown" : model)
                .tag("stream", String.valueOf(stream))
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public void recordTokens(long tokens) {
        if (tokens > 0) {
            Counter.builder("gateway.tokens.total").register(registry).increment(tokens);
        }
    }

    public void recordCache(String type, boolean hit) {
        Counter.builder("gateway.cache")
                .tag("type", type)
                .tag("result", hit ? "hit" : "miss")
                .register(registry)
                .increment();
    }

    public void recordDisconnect() {
        Counter.builder("gateway.disconnects").register(registry).increment();
    }

    public void recordError(String type) {
        Counter.builder("gateway.errors").tag("type", type).register(registry).increment();
    }

    public void recordCircuitOpen(String circuit) {
        Counter.builder("gateway.circuitbreaker.opens").tag("circuit", circuit).register(registry).increment();
    }

    public void recordBudgetWarning() {
        Counter.builder("gateway.budget.warnings").register(registry).increment();
    }
}
