package com.lumina.gateway.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 流式分片 choice。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChunkChoice(
        int index,
        ChunkDelta delta,
        @JsonProperty("finish_reason") String finishReason) {
}
