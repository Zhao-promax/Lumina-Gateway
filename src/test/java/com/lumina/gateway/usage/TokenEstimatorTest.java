package com.lumina.gateway.usage;

import com.lumina.gateway.api.dto.ChatMessage;
import com.lumina.gateway.api.dto.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEstimatorTest {

    @Test
    void estimateUsesDefaultCompletionWhenNoMaxTokens() {
        ChatRequest req = new ChatRequest();
        req.setMessages(List.of(new ChatMessage("user", "hello world", null)));
        // prompt: role(4) + content 11/4 + 1 = 4 + 2 + 1 = 7
        assertThat(TokenEstimator.estimate(req, 512)).isEqualTo(7 + 512);
    }

    @Test
    void estimateUsesMaxTokensWhenProvided() {
        ChatRequest req = new ChatRequest();
        req.setMessages(List.of(new ChatMessage("user", "hello world", null)));
        req.putExtra("max_tokens", 100);
        assertThat(TokenEstimator.estimate(req, 512)).isEqualTo(7 + 100);
    }
}
