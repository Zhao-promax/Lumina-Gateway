package com.lumina.gateway.routing;

import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 路由解析器：根据标签解析出「有序候选模型列表」，支撑主备故障转移与降级。
 * <p>
 * 候选顺序：primary 在前、backup 在后；降级开启时按价格升序（廉价优先）重排。
 * 路由规则存放于可替换的 {@link AtomicReference}，供 Nacos 热更更新。
 */
@Component
public class RouteResolver {

    private final AtomicReference<GatewayProperties.Routing> routingRef;

    public RouteResolver(GatewayProperties properties) {
        this.routingRef = new AtomicReference<>(properties.getRouting());
    }

    /**
     * Nacos 热更入口：整体替换路由规则。
     */
    public void updateRouting(GatewayProperties.Routing routing) {
        this.routingRef.set(routing);
    }

    /**
     * 解析候选模型列表（降级开关取全局配置）。
     */
    public List<RouteTarget> resolveCandidates(String routeTag) {
        return resolveCandidates(routeTag, routingRef.get().isDowngradeEnabled());
    }

    /**
     * 解析候选模型列表（显式指定是否降级，供预算触发降级使用）。
     */
    public List<RouteTarget> resolveCandidates(String routeTag, boolean downgrade) {
        GatewayProperties.Routing routing = routingRef.get();
        String effectiveTag = (routeTag == null || routeTag.isBlank())
                ? routing.getDefaultTag()
                : routeTag;

        GatewayProperties.Route route = routing.getRoutes().stream()
                .filter(r -> r.getTag().equals(effectiveTag))
                .findFirst()
                .orElseThrow(() -> new GatewayException(HttpStatus.BAD_REQUEST, "no_route",
                        "no route configured for tag: " + effectiveTag));

        List<GatewayProperties.ModelRef> refs = new ArrayList<>(route.getModels());
        if (downgrade) {
            refs.sort(Comparator.comparingDouble(GatewayProperties.ModelRef::getPrice));
        }

        return refs.stream()
                .map(r -> new RouteTarget(r.getProvider(), r.getModel()))
                .toList();
    }
}
