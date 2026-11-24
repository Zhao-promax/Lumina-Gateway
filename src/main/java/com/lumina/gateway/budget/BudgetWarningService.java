package com.lumina.gateway.budget;

import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 额度预警：预算用量达到 warn-ratio 阈值时告警（日志 + 指标计数）。
 * <p>
 * 按 租户:App 去重，用量回落到阈值以下后再次跨越会重新告警。
 */
@Component
public class BudgetWarningService {

    private static final Logger log = LoggerFactory.getLogger(BudgetWarningService.class);

    private final BudgetService budgetService;
    private final GatewayProperties properties;
    private final GatewayMetrics metrics;
    private final ConcurrentHashMap<String, Boolean> warned = new ConcurrentHashMap<>();

    public BudgetWarningService(BudgetService budgetService,
                                GatewayProperties properties,
                                GatewayMetrics metrics) {
        this.budgetService = budgetService;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 异步检查（fire-and-forget），不阻塞主链路。
     */
    public void check(String tenant, String appKey) {
        if (!properties.getBudget().isEnabled()) {
            return;
        }
        budgetService.remaining(tenant, appKey).subscribe(remaining -> {
            long total = properties.getBudget().getTotalLimit();
            long warnThreshold = (long) (total * (1 - properties.getBudget().getWarnRatio()));
            String key = tenant + ":" + appKey;
            if (remaining <= warnThreshold) {
                if (warned.putIfAbsent(key, true) == null) {
                    log.warn("budget warning: {} used {}/{} tokens", key, total - remaining, total);
                    metrics.recordBudgetWarning();
                }
            } else {
                warned.remove(key);
            }
        });
    }
}
