package com.lumina.gateway.budget;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InMemoryBudgetServiceTest {

    @Test
    void rejectsWhenOverDailyLimit() {
        InMemoryBudgetService service = new InMemoryBudgetService(100, 1000);
        StepVerifier.create(service.reserve("t", "app", 80)).expectNext(true).verifyComplete();
        StepVerifier.create(service.reserve("t", "app", 30)).expectNext(false).verifyComplete();
    }

    @Test
    void settleRefundsBudget() {
        InMemoryBudgetService service = new InMemoryBudgetService(100, 1000);
        service.reserve("t", "app", 50).block();
        service.settle("t", "app", -30).block();
        // 总预算剩余 = 1000 - 20 = 980
        StepVerifier.create(service.remaining("t", "app")).expectNext(980L).verifyComplete();
    }
}
