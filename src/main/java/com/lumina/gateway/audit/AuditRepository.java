package com.lumina.gateway.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 审计日志落库（MySQL，表结构见 deploy/schema.sql）。
 */
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class AuditRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditRepository.class);

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(AuditEvent event) {
        jdbc.update("""
                INSERT INTO audit_log(trace_id, tenant, app_key, provider, model, stream,
                    prompt_tokens, completion_tokens, latency_ms, status, cached, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """,
                event.traceId(), event.tenant(), event.appKey(), event.provider(), event.model(),
                event.stream() ? 1 : 0, event.promptTokens(), event.completionTokens(),
                event.latencyMs(), event.status(), event.cached() ? 1 : 0);
        log.debug("audit log inserted: traceId={}", event.traceId());
    }
}
