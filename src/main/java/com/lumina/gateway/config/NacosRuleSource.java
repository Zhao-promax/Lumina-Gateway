package com.lumina.gateway.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.budget.BudgetService;
import com.lumina.gateway.ratelimit.RateLimiter;
import com.lumina.gateway.resilience.ResilienceManager;
import com.lumina.gateway.routing.RouteResolver;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * Nacos 动态规则源（可选，默认关闭）。
 * <p>
 * 启用后（gateway.nacos.enabled=true）从 Nacos 拉取路由/熔断/限流/预算规则，并监听变更实时热更。
 * 启动时若 Nacos 尚未就绪会重试连接（最多 5 次 × 5s），仍失败则回退本地配置。
 * JSON 结构见 {@link GatewayRules}（样例 deploy/nacos-config-example.json）。
 */
@Component
@ConditionalOnProperty(name = "gateway.nacos.enabled", havingValue = "true")
public class NacosRuleSource {

    private static final Logger log = LoggerFactory.getLogger(NacosRuleSource.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 5000;

    private final GatewayProperties properties;
    private final RouteResolver routeResolver;
    private final ResilienceManager resilienceManager;
    private final RateLimiter rateLimiter;
    private final BudgetService budgetService;
    private final ObjectMapper objectMapper;

    public NacosRuleSource(GatewayProperties properties,
                           RouteResolver routeResolver,
                           ResilienceManager resilienceManager,
                           RateLimiter rateLimiter,
                           BudgetService budgetService,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.routeResolver = routeResolver;
        this.resilienceManager = resilienceManager;
        this.rateLimiter = rateLimiter;
        this.budgetService = budgetService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                doInit();
                log.info("nacos rule source initialized: {}:{}",
                        properties.getNacos().getDataId(), properties.getNacos().getGroup());
                return;
            } catch (Exception e) {
                log.warn("nacos init attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    log.error("nacos unavailable after {} attempts, fallback to local config", MAX_ATTEMPTS);
                    return;
                }
                sleep();
            }
        }
    }

    private void doInit() throws Exception {
        Properties props = new Properties();
        props.setProperty(PropertyKeyConst.SERVER_ADDR, properties.getNacos().getServerAddr());
        ConfigService configService = NacosFactory.createConfigService(props);

        String dataId = properties.getNacos().getDataId();
        String group = properties.getNacos().getGroup();

        String content = configService.getConfig(dataId, group, 5000);
        if (content != null && !content.isBlank()) {
            apply(content);
        }

        configService.addListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("nacos rules config changed, applying...");
                apply(configInfo);
            }
        });
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void apply(String json) {
        try {
            GatewayRules rules = objectMapper.readValue(json, GatewayRules.class);
            if (rules.routing() != null) {
                routeResolver.updateRouting(rules.routing());
            }
            if (rules.resilience() != null) {
                resilienceManager.updateResilience(rules.resilience());
            }
            if (rules.rateLimit() != null) {
                rateLimiter.updateRate(rules.rateLimit().getPermitsPerSecond());
            }
            if (rules.budget() != null) {
                budgetService.updateLimits(rules.budget().getDailyLimit(), rules.budget().getTotalLimit());
            }
            log.info("gateway rules updated from nacos");
        } catch (Exception e) {
            log.error("failed to apply nacos rules config: {}", e.getMessage());
        }
    }
}
