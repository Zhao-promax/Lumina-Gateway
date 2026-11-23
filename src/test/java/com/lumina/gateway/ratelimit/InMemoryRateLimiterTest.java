package com.lumina.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InMemoryRateLimiterTest {

    @Test
    void allowsWithinCapacityAndRejectsBurstBeyondCapacity() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(3);

        // 前 3 次放行，第 4 次突发超出容量拒绝
        assertThatAllows(limiter, 3);
        StepVerifier.create(limiter.tryAcquire("t", "app", "m"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void differentKeysHaveIndependentBuckets() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(1);
        assertThatAllows(limiter, 1);
        // 另一维度不受影响
        StepVerifier.create(limiter.tryAcquire("t", "app", "m2"))
                .expectNext(true)
                .verifyComplete();
    }

    private void assertThatAllows(RateLimiter limiter, int count) {
        for (int i = 0; i < count; i++) {
            StepVerifier.create(limiter.tryAcquire("t", "app", "m"))
                    .expectNext(true)
                    .verifyComplete();
        }
    }
}
