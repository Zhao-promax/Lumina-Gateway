package com.lumina.gateway.routing;

import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 基础标签路由：根据标签（X-Route-Tag 请求头，缺省用 default-tag）解析到 厂商 + 模型。
 * <p>
 * 阶段 1 为静态配置；阶段 2 起路由规则迁移至 Nacos，支持权重、主备、降级并热更。
 */
@Component
public class LabelRouter {

    private final GatewayProperties properties;

    public LabelRouter(GatewayProperties properties) {
        this.properties = properties;
    }

    public RouteTarget resolve(String routeTag) {
        String effectiveTag = (routeTag == null || routeTag.isBlank())
                ? properties.getRouting().getDefaultTag()
                : routeTag;

        return properties.getRouting().getRoutes().stream()
                .filter(r -> r.getTag().equals(effectiveTag))
                .findFirst()
                .map(r -> new RouteTarget(r.getProvider(), r.getModel()))
                .orElseThrow(() -> new GatewayException(HttpStatus.BAD_REQUEST, "no_route",
                        "no route configured for tag: " + effectiveTag));
    }
}
