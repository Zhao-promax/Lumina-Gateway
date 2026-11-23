package com.lumina.gateway.exception;

import org.springframework.http.HttpStatus;

/**
 * 网关统一业务异常，映射为 OpenAI 兼容错误响应。
 */
public class GatewayException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String code;

    public GatewayException(HttpStatus status, String type, String message) {
        this(status, type, null, message);
    }

    public GatewayException(HttpStatus status, String type, String code, String message) {
        super(message);
        this.status = status;
        this.type = type;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getCode() {
        return code;
    }
}
