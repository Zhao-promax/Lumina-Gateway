package com.lumina.gateway.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 非流式响应 choice。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Choice(
        int index,
        ChatMessage message,
        @JsonProperty("finish_reason") String finishReason) {
}
