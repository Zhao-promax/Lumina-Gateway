package com.lumina.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;
import java.util.Optional;

/**
 * 主密钥配置：从环境变量 GATEWAY_MASTER_KEY（32 字节 base64）构建 SecretCipher。
 * <p>
 * 未设置主密钥时返回空（密文配置将无法解密，仅支持明文）。生产建议从 KMS 或 Secret 注入。
 */
@Configuration
public class SecretConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecretConfiguration.class);

    @Bean
    public Optional<SecretCipher> secretCipher(@Value("${GATEWAY_MASTER_KEY:}") String masterKeyBase64) {
        if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
            log.warn("GATEWAY_MASTER_KEY not set; encrypted config values (ENC(...)) will not be decrypted");
            return Optional.empty();
        }
        byte[] key = Base64.getDecoder().decode(masterKeyBase64);
        return Optional.of(new SecretCipher(key));
    }
}
