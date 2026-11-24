-- Lumina Gateway 审计日志表（MySQL）
CREATE TABLE IF NOT EXISTS audit_log (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id          VARCHAR(64),
    tenant            VARCHAR(64),
    app_key           VARCHAR(64),
    provider          VARCHAR(32),
    model             VARCHAR(64),
    stream            TINYINT NOT NULL DEFAULT 0,
    prompt_tokens     INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    latency_ms        BIGINT NOT NULL DEFAULT 0,
    status            VARCHAR(32),
    cached            TINYINT NOT NULL DEFAULT 0,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trace (trace_id),
    INDEX idx_tenant_created (tenant, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
