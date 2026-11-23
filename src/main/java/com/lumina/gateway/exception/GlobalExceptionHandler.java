package com.lumina.gateway.exception;

import com.lumina.gateway.api.dto.ErrorBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.stream.Collectors;

/**
 * 全局异常处理，统一输出 OpenAI 兼容错误结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ErrorBody> handleGateway(GatewayException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorBody(new ErrorBody.ErrorDetail(e.getMessage(), e.getType(), null, e.getCode())));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorBody> handleValidation(WebExchangeBindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(new ErrorBody(new ErrorBody.ErrorDetail(message, "invalid_request_error", null, "validation_error")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleOther(Exception e) {
        log.error("unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody(new ErrorBody.ErrorDetail("internal server error", "internal_error", null, null)));
    }
}
