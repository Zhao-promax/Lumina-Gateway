package com.lumina.gateway.security;

/**
 * 主密钥轮换：用旧主密钥解密、新主密钥重加密存量密文。
 * <p>
 * 轮换流程：生成新主密钥 → 遍历存量密文 reEncrypt → 原子切换新主密钥 → 下线旧主密钥。
 */
public class KeyRotationService {

    /**
     * 用新主密钥重加密一个旧主密钥加密的密文。
     */
    public String reEncrypt(String encryptedValue, SecretCipher oldCipher, SecretCipher newCipher) {
        return newCipher.encrypt(oldCipher.decrypt(encryptedValue));
    }
}
