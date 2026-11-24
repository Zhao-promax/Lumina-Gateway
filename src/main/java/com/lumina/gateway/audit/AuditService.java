package com.lumina.gateway.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异步审计：结构化日志（始终）+ 可选 RabbitMQ 投递。
 * <p>
 * 审计在独立线程池异步执行（有界队列 + 溢出丢弃），不阻塞响应主链路。
 */
@Component
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    private final GatewayProperties properties;
    private final Optional<RabbitTemplate> rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService auditExecutor;

    public AuditService(GatewayProperties properties,
                        Optional<RabbitTemplate> rabbitTemplate,
                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.auditExecutor = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10000),
                r -> {
                    Thread t = new Thread(r, "audit-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardPolicy());
    }

    public void record(AuditEvent event) {
        try {
            auditExecutor.execute(() -> doRecord(event));
        } catch (Exception e) {
            log.warn("audit queue overflow, dropped event");
        }
    }

    private void doRecord(AuditEvent event) {
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
