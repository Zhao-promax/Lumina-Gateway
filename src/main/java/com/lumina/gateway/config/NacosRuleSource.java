package com.lumina.gateway.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 启用后（gateway.nacos.enabled=true）从 Nacos 拉取路由规则 JSON，并监听变更实时热更。
 * JSON 结构与 gateway.routing 一致，例如：
 * <pre>
 * {
 *   "defaultTag": "default",
 *   "downgradeEnabled": false,
 *   "routes": [ {"tag":"default","models":[{"provider":"deepseek","model":"deepseek-chat","price":0.002,"weight":100}]} ]
 * }
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "gateway.nacos.enabled", havingValue = "true")
public class NacosRuleSource {

    private static final Logger log = LoggerFactory.getLogger(NacosRuleSource.class);

    private final GatewayProperties properties;
    private final RouteResolver routeResolver;
    private final ResilienceManager resilienceManager;
    private final ObjectMapper objectMapper;

    public NacosRuleSource(GatewayProperties properties,
                           RouteResolver routeResolver,
                           ResilienceManager resilienceManager,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.routeResolver = routeResolver;
        this.resilienceManager = resilienceManager;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
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
                    log.info("nacos routing config changed, applying...");
                    apply(configInfo);
                }
            });
            log.info("nacos rule source initialized: {}:{}", dataId, group);
        } catch (Exception e) {
            log.error("failed to init nacos rule source, fallback to local config", e);
        }
    }

    private void apply(String json) {
        try {
            GatewayProperties.Routing routing = objectMapper.readValue(json, GatewayProperties.Routing.class);
            routeResolver.updateRouting(routing);
            resilienceManager.reload();
            log.info("routing rules updated from nacos");
        } catch (Exception e) {
            log.error("failed to apply nacos routing config: {}", e.getMessage());
        }
    }
}
