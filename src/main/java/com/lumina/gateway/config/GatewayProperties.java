package com.lumina.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关配置（绑定 application.yml 中 gateway.* 前缀）。
 * <p>
 * 阶段 2：路由支持「有序候选模型列表」以支撑主备故障转移与降级；新增熔断/超时/重试、限流、Nacos 配置项。
 * Nacos 启用后，路由规则可热更新（见 {@code NacosRuleSource}）。
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Auth auth = new Auth();
    private Routing routing = new Routing();
    private Resilience resilience = new Resilience();
    private RateLimit rateLimit = new RateLimit();
    private Nacos nacos = new Nacos();
    private Budget budget = new Budget();
    private Cache cache = new Cache();
    private Moderation moderation = new Moderation();
    private Audit audit = new Audit();
    private Mock mock = new Mock();
    private Sse sse = new Sse();
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

    public Resilience getResilience() {
        return resilience;
    }

    public void setResilience(Resilience resilience) {
        this.resilience = resilience;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Nacos getNacos() {
        return nacos;
    }

    public void setNacos(Nacos nacos) {
        this.nacos = nacos;
    }

    public Budget getBudget() {
        return budget;
    }

    public void setBudget(Budget budget) {
        this.budget = budget;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Moderation getModeration() {
        return moderation;
    }

    public void setModeration(Moderation moderation) {
        this.moderation = moderation;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit;
    }

    public Mock getMock() {
        return mock;
    }

    public void setMock(Mock mock) {
        this.mock = mock;
    }

    public Sse getSse() {
        return sse;
    }

    public void setSse(Sse sse) {
        this.sse = sse;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    // ===== 鉴权 =====

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

    // ===== 路由 =====

    public static class Routing {
        private String defaultTag = "default";
        private boolean downgradeEnabled = false;
        private List<Route> routes = new ArrayList<>();

        public String getDefaultTag() {
            return defaultTag;
        }

        public void setDefaultTag(String defaultTag) {
            this.defaultTag = defaultTag;
        }

        public boolean isDowngradeEnabled() {
            return downgradeEnabled;
        }

        public void setDowngradeEnabled(boolean downgradeEnabled) {
            this.downgradeEnabled = downgradeEnabled;
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
        private List<ModelRef> models = new ArrayList<>();

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public List<ModelRef> getModels() {
            return models;
        }

        public void setModels(List<ModelRef> models) {
            this.models = models;
        }
    }

    public static class ModelRef {
        private String provider;
        private String model;
        private double price = 0;
        private int weight = 100;

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

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }
    }

    // ===== 熔断 / 超时 / 重试 =====

    public static class Resilience {
        private CircuitBreaker circuitBreaker = new CircuitBreaker();
        private long timeoutMs = 30000;
        private Retry retry = new Retry();

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public Retry getRetry() {
            return retry;
        }

        public void setRetry(Retry retry) {
            this.retry = retry;
        }
    }

    public static class CircuitBreaker {
        private float failureRateThreshold = 50;
        private int slidingWindowSize = 10;
        private int minimumNumberOfCalls = 5;
        private long waitDurationInOpenStateMs = 10000;

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public long getWaitDurationInOpenStateMs() {
            return waitDurationInOpenStateMs;
        }

        public void setWaitDurationInOpenStateMs(long waitDurationInOpenStateMs) {
            this.waitDurationInOpenStateMs = waitDurationInOpenStateMs;
        }
    }

    public static class Retry {
        private int maxAttempts = 2;
        private long waitMs = 200;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getWaitMs() {
            return waitMs;
        }

        public void setWaitMs(long waitMs) {
            this.waitMs = waitMs;
        }
    }

    // ===== 限流 =====

    public static class RateLimit {
        private boolean enabled = true;
        private String backend = "memory";   // memory | redis
        private int permitsPerSecond = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBackend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public int getPermitsPerSecond() {
            return permitsPerSecond;
        }

        public void setPermitsPerSecond(int permitsPerSecond) {
            this.permitsPerSecond = permitsPerSecond;
        }
    }

    // ===== Nacos =====

    public static class Nacos {
        private boolean enabled = false;
        private String serverAddr = "localhost:8848";
        private String dataId = "lumina-gateway-routing";
        private String group = "DEFAULT_GROUP";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getServerAddr() {
            return serverAddr;
        }

        public void setServerAddr(String serverAddr) {
            this.serverAddr = serverAddr;
        }

        public String getDataId() {
            return dataId;
        }

        public void setDataId(String dataId) {
            this.dataId = dataId;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }
    }

    // ===== 预算配额 =====

    public static class Budget {
        private boolean enabled = true;
        private String backend = "memory";   // memory | redis
        private long dailyLimit = 100000;
        private long totalLimit = 1000000;
        private long defaultCompletionTokens = 512;
        private double warnRatio = 0.8;              // 用量达到该比例触发额度预警
        private double downgradeThresholdRatio = 0.2; // 剩余预算低于该比例触发降级

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBackend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public long getDailyLimit() {
            return dailyLimit;
        }

        public void setDailyLimit(long dailyLimit) {
            this.dailyLimit = dailyLimit;
        }

        public long getTotalLimit() {
            return totalLimit;
        }

        public void setTotalLimit(long totalLimit) {
            this.totalLimit = totalLimit;
        }

        public long getDefaultCompletionTokens() {
            return defaultCompletionTokens;
        }

        public void setDefaultCompletionTokens(long defaultCompletionTokens) {
            this.defaultCompletionTokens = defaultCompletionTokens;
        }

        public double getWarnRatio() {
            return warnRatio;
        }

        public void setWarnRatio(double warnRatio) {
            this.warnRatio = warnRatio;
        }

        public double getDowngradeThresholdRatio() {
            return downgradeThresholdRatio;
        }

        public void setDowngradeThresholdRatio(double downgradeThresholdRatio) {
            this.downgradeThresholdRatio = downgradeThresholdRatio;
        }
    }

    // ===== 缓存 =====

    public static class Cache {
        private boolean exactEnabled = true;
        private long exactTtlSeconds = 3600;
        private boolean semanticEnabled = false;
        private double semanticThreshold = 0.9;

        public boolean isExactEnabled() {
            return exactEnabled;
        }

        public void setExactEnabled(boolean exactEnabled) {
            this.exactEnabled = exactEnabled;
        }

        public long getExactTtlSeconds() {
            return exactTtlSeconds;
        }

        public void setExactTtlSeconds(long exactTtlSeconds) {
            this.exactTtlSeconds = exactTtlSeconds;
        }

        public boolean isSemanticEnabled() {
            return semanticEnabled;
        }

        public void setSemanticEnabled(boolean semanticEnabled) {
            this.semanticEnabled = semanticEnabled;
        }

        public double getSemanticThreshold() {
            return semanticThreshold;
        }

        public void setSemanticThreshold(double semanticThreshold) {
            this.semanticThreshold = semanticThreshold;
        }
    }

    // ===== 内容风控 =====

    public static class Moderation {
        private boolean enabled = false;
        private List<String> words = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getWords() {
            return words;
        }

        public void setWords(List<String> words) {
            this.words = words;
        }
    }

    // ===== 审计 =====

    public static class Audit {
        private boolean mqEnabled = false;
        private String queue = "gateway.audit";

        public boolean isMqEnabled() {
            return mqEnabled;
        }

        public void setMqEnabled(boolean mqEnabled) {
            this.mqEnabled = mqEnabled;
        }

        public String getQueue() {
            return queue;
        }

        public void setQueue(String queue) {
            this.queue = queue;
        }
    }

    // ===== Mock 厂商（压测/演示用，默认关闭） =====

    public static class Mock {
        private boolean enabled = false;
        private int chunkCount = 50;
        private long chunkDelayMs = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public void setChunkCount(int chunkCount) {
            this.chunkCount = chunkCount;
        }

        public long getChunkDelayMs() {
            return chunkDelayMs;
        }

        public void setChunkDelayMs(long chunkDelayMs) {
            this.chunkDelayMs = chunkDelayMs;
        }
    }

    // ===== SSE 流治理 =====

    public static class Sse {
        private int bufferSize = 256;        // 背压分片缓冲上限
        private long idleTimeoutMs = 60000;   // 闲置流超时回收

        public int getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        public long getIdleTimeoutMs() {
            return idleTimeoutMs;
        }

        public void setIdleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
        }
    }

    // ===== 厂商 =====

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
