package com.lumina.gateway.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 可动态热更的网关规则集合（Nacos 配置模型）。
 * <p>
 * 各字段可为 null（部分更新），Nacos 变更时分别应用到对应组件。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GatewayRules(
        GatewayProperties.Routing routing,
        GatewayProperties.Resilience resilience,
        GatewayProperties.RateLimit rateLimit,
        GatewayProperties.Budget budget) {
}
