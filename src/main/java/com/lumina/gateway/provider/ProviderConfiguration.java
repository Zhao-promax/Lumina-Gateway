package com.lumina.gateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.security.SecretCipher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * 适配器装配：根据 gateway.providers 配置注册启用的厂商适配器。
 * <p>
 * 厂商密钥支持 ENC(base64) 密文，启动时用主密钥解密；明文直接使用。
 * 新增厂商适配器：新建 Provider 类并在 {@link #providerRegistry} 中追加一行 register 调用即可。
 */
@Configuration
public class ProviderConfiguration {

    @Bean
    public ProviderRegistry providerRegistry(GatewayProperties properties,
                                             ObjectMapper objectMapper,
                                             Optional<SecretCipher> cipher) {
        ProviderRegistry registry = new ProviderRegistry();
        register(registry, properties, objectMapper, cipher, "deepseek", DeepSeekProvider::new);
        register(registry, properties, objectMapper, cipher, "qwen", QwenProvider::new);
        if (properties.getMock().isEnabled()) {
            registry.register(new MockProvider(properties.getMock().getChunkCount(), properties.getMock().getChunkDelayMs()));
        }
        return registry;
    }

    @FunctionalInterface
    private interface ProviderFactory {
        OpenAiCompatibleProvider create(String baseUrl, String apiKey, ObjectMapper objectMapper);
    }

    private void register(ProviderRegistry registry, GatewayProperties properties, ObjectMapper objectMapper,
                          Optional<SecretCipher> cipher, String name, ProviderFactory factory) {
        GatewayProperties.ProviderConfig cfg = properties.getProviders().get(name);
        if (cfg != null && cfg.isEnabled()) {
            String rawKey = cfg.getApiKey();
            String apiKey = cipher.map(c -> c.decryptIfEncrypted(rawKey)).orElse(rawKey);
            registry.register(factory.create(cfg.getBaseUrl(), apiKey, objectMapper));
        }
    }
}
