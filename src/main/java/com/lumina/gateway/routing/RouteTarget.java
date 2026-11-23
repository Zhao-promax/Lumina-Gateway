package com.lumina.gateway.routing;

/**
 * 路由解析结果：目标厂商 + 目标模型。
 */
public record RouteTarget(String provider, String model) {
}
