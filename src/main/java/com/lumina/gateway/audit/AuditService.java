package com.lumina.gateway.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 异步审计：结构化日志（始终）+ 可选 RabbitMQ 投递。
 * <p>
 * 主链路不阻塞：MQ 投递为 fire-and-forget；消费者异步落库（见 AuditConfiguration）。
 */
@Component
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    private final GatewayProperties properties;
    private final Optional<RabbitTemplate> rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(GatewayProperties properties,
                        Optional<RabbitTemplate> rabbitTemplate,
                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(AuditEvent event) {
        // 结构化日志（始终可用，便于日志采集）
        try {
            log.info("{}", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.info("audit serialize failed: {}", e.getMessage());
        }
        // 可选 MQ 异步投递
        if (properties.getAudit().isMqEnabled() && rabbitTemplate.isPresent()) {
            try {
                rabbitTemplate.get().convertAndSend(properties.getAudit().getQueue(), event);
            } catch (Exception e) {
                log.warn("audit mq publish failed: {}", e.getMessage());
            }
        }
    }
}
