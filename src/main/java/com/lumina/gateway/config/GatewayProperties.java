package com.lumina.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关配置（绑定 application.yml 中 gateway.* 前缀）。
 * 阶段 1 为静态配置；阶段 2 起将路由/限流/熔断等迁移至 Nacos 动态配置。
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Auth auth = new Auth();
    private Routing routing = new Routing();
    private Map<String, ProviderConfig> providers = new HashMap<>();

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Routing getRouting() {
        return routing;
    }

    public void setRouting(Routing routing) {
        this.routing = routing;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    public static class Auth {
        private boolean enabled = true;
        private long timestampWindowSeconds = 300;
        private List<App> apps = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTimestampWindowSeconds() {
            return timestampWindowSeconds;
        }

        public void setTimestampWindowSeconds(long timestampWindowSeconds) {
            this.timestampWindowSeconds = timestampWindowSeconds;
        }

        public List<App> getApps() {
            return apps;
        }

        public void setApps(List<App> apps) {
            this.apps = apps;
        }
    }

    public static class App {
        private String appKey;
        private String appSecret;
        private String tenant;

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }
    }

    public static class Routing {
        private String defaultTag = "default";
        private List<Route> routes = new ArrayList<>();

        public String getDefaultTag() {
            return defaultTag;
        }

        public void setDefaultTag(String defaultTag) {
            this.defaultTag = defaultTag;
        }

        public List<Route> getRoutes() {
            return routes;
        }

        public void setRoutes(List<Route> routes) {
            this.routes = routes;
        }
    }

    public static class Route {
        private String tag;
        private String provider;
        private String model;

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class ProviderConfig {
        private String baseUrl;
        private String apiKey;
        private boolean enabled = true;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
