package com.lumina.gateway.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OpenAI 兼容错误响应体：{"error":{"message":...,"type":...,"param":...,"code":...}}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(ErrorDetail error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(String message, String type, String param, String code) {
    }
}
