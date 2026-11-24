package com.lumina.gateway.metrics;

import com.lumina.gateway.audit.AuditEvent;
import com.lumina.gateway.audit.AuditService;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 观测门面：聚合指标埋点与审计记录，供编排层统一调用。
 */
@Component
public class GatewayObserver {

    private final GatewayMetrics metrics;
    private final AuditService audit;

    public GatewayObserver(GatewayMetrics metrics, AuditService audit) {
        this.metrics = metrics;
        this.audit = audit;
    }

    public Timer.Sample start() {
        return metrics.start();
    }

    public void recordSuccess(Timer.Sample sample, AuditEvent event) {
        metrics.recordRequest(sample, event.provider(), event.model(), event.stream(), "ok");
        metrics.recordTokens(event.promptTokens() + event.completionTokens());
        audit.record(event);
    }

    public void recordError(Timer.Sample sample, AuditEvent event, String type) {
        metrics.recordError(type);
        metrics.recordRequest(sample, event.provider(), event.model(), event.stream(), "error");
        audit.record(event);
    }

    public void recordCache(String type, boolean hit) {
        metrics.recordCache(type, hit);
    }

    public void recordDisconnect() {
        metrics.recordDisconnect();
    }

    public void recordStream(Timer.Sample sample, AuditEvent event, String status) {
        metrics.recordRequest(sample, event.provider(), event.model(), true, status);
        if ("error".equals(status)) {
            metrics.recordError(event.status());
        }
        audit.record(event);
    }

    public void recordStreamTokens(long tokens) {
        metrics.recordTokens(tokens);
    }
}
