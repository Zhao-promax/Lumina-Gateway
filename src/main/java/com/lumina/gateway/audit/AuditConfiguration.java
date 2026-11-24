package com.lumina.gateway.audit;

import com.lumina.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 审计消费（可选，gateway.audit.mq-enabled=true 时启用）。
 * <p>
 * 消费者异步落库；此处以结构化日志代表 MySQL sink，生产可替换为 JdbcTemplate 写入 audit_log 表
 * （建表 SQL 见 deploy/schema.sql）。
 */
@Configuration
@ConditionalOnProperty(name = "gateway.audit.mq-enabled", havingValue = "true")
public class AuditConfiguration {

    private static final Logger log = LoggerFactory.getLogger("AUDIT.CONSUMER");

    private final ObjectProvider<AuditRepository> auditRepository;

    public AuditConfiguration(ObjectProvider<AuditRepository> auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Bean
    public Queue auditQueue(GatewayProperties properties) {
        return new Queue(properties.getAudit().getQueue(), true);
    }

    @RabbitListener(queues = "#{@auditQueue.name}")
    public void onAudit(AuditEvent event) {
        log.info("audit consumed: traceId={}, tenant={}, model={}, tokens={}/{}",
                event.traceId(), event.tenant(), event.model(), event.promptTokens(), event.completionTokens());
        AuditRepository repo = auditRepository.getIfAvailable();
        if (repo != null) {
            try {
                repo.insert(event);
            } catch (Exception e) {
                log.warn("audit db insert failed: {}", e.getMessage());
            }
        }
    }
}
