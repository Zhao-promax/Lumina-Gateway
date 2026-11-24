package com.lumina.gateway.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 密钥加解密（AES-256-GCM）。
 * <p>
 * 用于加密存储厂商密钥 / AppSecret，密文格式为 base64(IV[12] + ciphertext)。
 * 主密钥建议从 KMS 或环境变量注入（32 字节），不落库。
 */
public class SecretCipher {

    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String ENC_PREFIX = "ENC(";

    private final SecretKey key;

    public SecretCipher(byte[] keyBytes) {
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES key must be 16/24/32 bytes, got " + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }

    /**
     * 配置值是否为密文（形如 ENC(base64)）。
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX) && value.endsWith(")");
    }

    /**
     * 若为 ENC(...) 密文则解密，否则原样返回。
     */
    public String decryptIfEncrypted(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        return decrypt(value.substring(ENC_PREFIX.length(), value.length() - 1));
    }
}
