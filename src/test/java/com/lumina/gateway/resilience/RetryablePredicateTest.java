package com.lumina.gateway.resilience;

import com.lumina.gateway.exception.GatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RetryablePredicateTest {

    @Test
    void retriesOn5xx() {
        assertThat(RetryablePredicate.isRetryable(
                new GatewayException(HttpStatus.BAD_GATEWAY, "upstream_error", "boom"))).isTrue();
    }

    @Test
    void retriesOnConnectionIOException() {
        assertThat(RetryablePredicate.isRetryable(new IOException("connection reset"))).isTrue();
    }

    @Test
    void doesNotRetryOn4xx() {
        assertThat(RetryablePredicate.isRetryable(
                new GatewayException(HttpStatus.BAD_REQUEST, "invalid_request_error", "bad"))).isFalse();
    }

    @Test
    void doesNotRetryOnRateLimit429() {
        assertThat(RetryablePredicate.isRetryable(
                new GatewayException(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_error", "limited"))).isFalse();
    }

    @Test
    void doesNotRetryOnAuth401() {
        assertThat(RetryablePredicate.isRetryable(
                new GatewayException(HttpStatus.UNAUTHORIZED, "authentication_error", "unauth"))).isFalse();
    }

    @Test
    void doesNotRetryOnGenericRuntimeException() {
        assertThat(RetryablePredicate.isRetryable(new RuntimeException("other"))).isFalse();
    }
}
