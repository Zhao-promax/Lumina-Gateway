package com.lumina.gateway.provider;

import com.lumina.gateway.exception.GatewayException;
import com.lumina.gateway.provider.spi.ModelProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 厂商适配器注册中心，按 provider 名称索引。
 */
@Component
public class ProviderRegistry {

    private final Map<String, ModelProvider> providers = new ConcurrentHashMap<>();

    public void register(ModelProvider provider) {
        providers.put(provider.provider(), provider);
    }

    public ModelProvider get(String name) {
        ModelProvider provider = providers.get(name);
        if (provider == null) {
            throw new GatewayException(HttpStatus.BAD_REQUEST, "provider_not_found",
                    "provider not found: " + name);
        }
        return provider;
    }
}
