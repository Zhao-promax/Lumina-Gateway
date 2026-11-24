package com.lumina.gateway.cache;

import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 精确 Prompt 哈希缓存。
 * <p>
 * 仅对「确定性输出」启用：temperature 必须显式为 0，否则不同调用结果不确定，命中会返回错误答案。
 * 阶段 3 使用内存实现（带 TTL 与容量上限）；多实例可切换 Redis 后端。
 */
@Component
public class ExactCacheService {

    private static final Logger log = LoggerFactory.getLogger(ExactCacheService.class);

    private static final int MAX_ENTRIES = 10000;

    private final GatewayProperties properties;
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public ExactCacheService(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * 是否可命中精确缓存：仅 temperature=0（确定性）且开关开启。
     */
    public boolean isCacheable(ChatRequest request) {
        return properties.getCache().isExactEnabled()
                && request.getTemperature() != null
                && request.getTemperature() == 0.0;
    }

    /**
     * 生成缓存键：model + messages + temperature + max_tokens 的 SHA-256。
     */
    public String key(ChatRequest request, String model) {
        StringBuilder sb = new StringBuilder();
        sb.append(model).append('|');
        if (request.getMessages() != null) {
            for (var m : request.getMessages()) {
                sb.append(m.role()).append(':').append(m.content()).append('|');
            }
        }
        sb.append("t=").append(request.getTemperature()).append('|');
        sb.append("mt=").append(request.getExtra().get("max_tokens"));
        return sha256(sb.toString());
    }

    public ChatResponse get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            store.remove(key);
            return null;
        }
        return entry.response;
    }

    public void put(String key, ChatResponse response) {
        if (store.size() >= MAX_ENTRIES) {
            evictOne();
        }
        long ttlMs = properties.getCache().getExactTtlSeconds() * 1000;
        store.put(key, new Entry(response, System.currentTimeMillis() + ttlMs));
        log.debug("exact cache stored, key={}", key);
    }

    private void evictOne() {
        // 简单驱逐：删除任意一个已过期或任意条目
        store.entrySet().stream().findAny().ifPresent(e -> store.remove(e.getKey()));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record Entry(ChatResponse response, long expireAt) {
    }
}
