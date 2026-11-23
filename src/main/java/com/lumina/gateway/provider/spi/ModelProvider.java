package com.lumina.gateway.provider.spi;

import com.lumina.gateway.api.dto.ChatChunk;
import com.lumina.gateway.api.dto.ChatRequest;
import com.lumina.gateway.api.dto.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 厂商适配器统一 SPI 接口。
 * <p>
 * 各厂商独立实现，隔离协议差异；新增厂商仅需新增实现类，主流程零改动。
 */
public interface ModelProvider {

    /**
     * 厂商标识，如 deepseek / qwen。
     */
    String provider();

    /**
     * 非流式调用。
     */
    Mono<ChatResponse> chat(ChatRequest request);

    /**
     * 流式调用，返回归一化后的标准 chunk 序列（不含 [DONE]）。
     */
    Flux<ChatChunk> chatStream(ChatRequest request);

    /**
     * 是否承载该模型。
     */
    boolean supports(String model);
}
