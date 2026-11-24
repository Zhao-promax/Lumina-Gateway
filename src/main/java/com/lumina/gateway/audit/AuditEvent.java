package com.lumina.gateway.audit;

/**
 * 审计事件（调用明细，用于成本对账与安全审计）。
 */
public record AuditEvent(
        String traceId,
        String tenant,
        String appKey,
        String provider,
        String model,
        boolean stream,
        long promptTokens,
        long completionTokens,
        long latencyMs,
        String status,
        boolean cached,
        long timestamp) {
}
