package com.lumina.gateway.cache;

import com.lumina.gateway.api.dto.ChatMessage;
import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.api.dto.Usage;
import com.lumina.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExactCacheServiceTest {

    @Test
    void cacheableOnlyWhenTemperatureIsZero() {
        ExactCacheService cache = new ExactCacheService(new GatewayProperties());
        ChatRequest req = new ChatRequest();
        req.setMessages(List.of(new ChatMessage("user", "hi", null)));

        assertThat(cache.isCacheable(req)).isFalse();   // temperature null

        req.setTemperature(0.0);
        assertThat(cache.isCacheable(req)).isTrue();

        req.setTemperature(1.0);
        assertThat(cache.isCacheable(req)).isFalse();
    }

    @Test
    void storesAndRetrieves() {
        ExactCacheService cache = new ExactCacheService(new GatewayProperties());
        ChatResponse resp = new ChatResponse("id", "chat.completion", 0L, "m",
                List.of(), new Usage(1, 1, 2));

        cache.put("key1", resp);
        assertThat(cache.get("key1")).isEqualTo(resp);
        assertThat(cache.get("key2")).isNull();
    }

    @Test
    void keyIsStableAndModelSensitive() {
        ExactCacheService cache = new ExactCacheService(new GatewayProperties());
        ChatRequest req = new ChatRequest();
        req.setMessages(List.of(new ChatMessage("user", "hi", null)));
        req.setTemperature(0.0);

        assertThat(cache.key(req, "deepseek-chat")).isEqualTo(cache.key(req, "deepseek-chat"));
        assertThat(cache.key(req, "deepseek-chat")).isNotEqualTo(cache.key(req, "qwen-plus"));
    }
}
