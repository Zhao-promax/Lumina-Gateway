package com.lumina.gateway.resilience;

import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.metrics.GatewayMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resilience4j 管理器：按「provider:model」维度管理熔断器，统一提供非流式/流式的装饰入口。
 * <p>
 * 非流式：Retry(外层) → CircuitBreaker → TimeLimiter(内层)；
 * 流式：仅 CircuitBreaker（流式请求产生首字节后不重试，也不设总时长超时）。
 */
@Component
public class ResilienceManager {

    private final GatewayProperties properties;
    private final GatewayMetrics metrics;
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private volatile Retry retry;
    private volatile TimeLimiter timeLimiter;

    public ResilienceManager(GatewayProperties properties, GatewayMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
        this.retry = buildRetry();
        this.timeLimiter = buildTimeLimiter();
    }

    public <T> Mono<T> decorateMono(String key, Mono<T> source) {
        CircuitBreaker circuitBreaker = circuitBreakers.computeIfAbsent(key, this::buildCircuitBreaker);
        return source
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .transformDeferred(RetryOperator.of(retry));
    }

    public <T> Flux<T> decorateFlux(String key, Flux<T> source) {
        CircuitBreaker circuitBreaker = circuitBreakers.computeIfAbsent(key, this::buildCircuitBreaker);
        return source.transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }

    /**
     * 配置变更后重建全部熔断器/重试/超时实例（供 Nacos 热更调用）。
     */
    public void reload() {
        this.retry = buildRetry();
        this.timeLimiter = buildTimeLimiter();
        this.circuitBreakers.clear();
    }

    private CircuitBreaker buildCircuitBreaker(String name) {
        GatewayProperties.CircuitBreaker cb = properties.getResilience().getCircuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(cb.getFailureRateThreshold())
                .slidingWindowSize(cb.getSlidingWindowSize())
                .minimumNumberOfCalls(cb.getMinimumNumberOfCalls())
                .waitDurationInOpenState(Duration.ofMillis(cb.getWaitDurationInOpenStateMs()))
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of(name, config);
        // 熔断打开时埋点计数（熔断触发次数）
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.StateTransition t = event.getStateTransition();
            if (t == CircuitBreaker.StateTransition.CLOSED_TO_OPEN
                    || t == CircuitBreaker.StateTransition.HALF_OPEN_TO_OPEN) {
                metrics.recordCircuitOpen(name);
            }
        });
        return circuitBreaker;
    }

    private Retry buildRetry() {
        GatewayProperties.Retry r = properties.getResilience().getRetry();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(r.getMaxAttempts())
                .waitDuration(Duration.ofMillis(r.getWaitMs()))
                .retryOnException(RetryablePredicate::isRetryable)
                .build();
        return Retry.of("gateway", config);
    }

    private TimeLimiter buildTimeLimiter() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(properties.getResilience().getTimeoutMs()))
                .build();
        return TimeLimiter.of(config);
    }
}
