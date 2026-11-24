package com.lumina.gateway.provider;

import com.lumina.gateway.api.dto.ChatChunk;
import com.lumina.gateway.api.dto.ChatMessage;
import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.api.dto.ChatResponse;
import com.lumina.gateway.api.dto.Choice;
import com.lumina.gateway.api.dto.ChunkChoice;
import com.lumina.gateway.api.dto.ChunkDelta;
import com.lumina.gateway.api.dto.Usage;
import com.lumina.gateway.provider.spi.ModelProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Mock 厂商（压测/演示用，默认关闭，gateway.mock.enabled=true 时注册）。
 * <p>
 * 无真实模型调用，返回固定响应；流式按固定节奏产出 chunk，用于并发压测 SSE 长连接链路。
 */
public class MockProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(MockProvider.class);

    private final int chunkCount;
    private final long chunkDelayMs;

    public MockProvider(int chunkCount, long chunkDelayMs) {
        this.chunkCount = chunkCount;
        this.chunkDelayMs = chunkDelayMs;
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public boolean supports(String model) {
        return true;
    }

    @Override
    public Mono<ChatResponse> chat(ChatRequest request) {
        long now = System.currentTimeMillis() / 1000;
        return Mono.just(new ChatResponse("mock-id", "chat.completion", now, request.getModel(),
                List.of(new Choice(0, new ChatMessage("assistant", "这是模拟响应", null), "stop")),
                new Usage(10, 20, 30)));
    }

    @Override
    public Flux<ChatChunk> chatStream(ChatRequest request) {
        long now = System.currentTimeMillis() / 1000;
        return Flux.interval(Duration.ofMillis(chunkDelayMs))
                .take(chunkCount)
                .map(i -> new ChatChunk("mock-id", "chat.completion.chunk", now, request.getModel(),
                        List.of(new ChunkChoice(0,
                                new ChunkDelta(i == 0 ? "assistant" : null, "token-" + i),
                                (i == chunkCount - 1) ? "stop" : null)),
                        (i == chunkCount - 1) ? new Usage(10, chunkCount, 10 + chunkCount) : null))
                .doOnCancel(() -> log.info("provider=mock downstream stream cancelled"));
    }
}
