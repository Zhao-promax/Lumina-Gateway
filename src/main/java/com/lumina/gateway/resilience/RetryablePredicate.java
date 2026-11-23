package com.lumina.gateway.resilience;

import com.lumina.gateway.exception.GatewayException;

import java.io.IOException;

/**
 * 可重试异常判定。
 * <p>
 * 仅对「连接类异常」与「上游 5xx 服务异常」重试；4xx、429、401、内容风控、超时等一律不重试，
 * 避免无效重试带来额外计费。
 */
public final class RetryablePredicate {

    private RetryablePredicate() {
    }

    public static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof GatewayException ge) {
            return ge.getStatus().value() >= 500;
        }
        // 遍历 cause 链，识别 WebClient 包装的连接异常
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
