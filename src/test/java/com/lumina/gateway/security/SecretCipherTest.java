package com.lumina.gateway.security;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    @Test
    void roundTrip() {
        SecretCipher cipher = new SecretCipher(new byte[32]);
        String encrypted = cipher.encrypt("sk-secret-key");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-secret-key");
    }

    @Test
    void differentKeyFailsToDecrypt() {
        byte[] key1 = new byte[32];
        Arrays.fill(key1, (byte) 1);
        byte[] key2 = new byte[32];
        Arrays.fill(key2, (byte) 2);
        SecretCipher c1 = new SecretCipher(key1);
        SecretCipher c2 = new SecretCipher(key2);
        String encrypted = c1.encrypt("secret");
        assertThatThrownBy(() -> c2.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rotationReEncryptsWithNewKey() {
        byte[] oldKey = new byte[32];
        Arrays.fill(oldKey, (byte) 1);
        byte[] newKey = new byte[32];
        Arrays.fill(newKey, (byte) 2);
        SecretCipher oldCipher = new SecretCipher(oldKey);
        SecretCipher newCipher = new SecretCipher(newKey);
        KeyRotationService rotation = new KeyRotationService();

        String encrypted = oldCipher.encrypt("secret");
        String reEncrypted = rotation.reEncrypt(encrypted, oldCipher, newCipher);
        assertThat(newCipher.decrypt(reEncrypted)).isEqualTo("secret");
    }

    @Test
    void decryptsEncFormatValue() {
        SecretCipher cipher = new SecretCipher(new byte[32]);
        String encrypted = cipher.encrypt("sk-real-key");
        String encValue = "ENC(" + encrypted + ")";

        assertThat(SecretCipher.isEncrypted(encValue)).isTrue();
        assertThat(SecretCipher.isEncrypted("sk-plain")).isFalse();
        assertThat(cipher.decryptIfEncrypted(encValue)).isEqualTo("sk-real-key");
        assertThat(cipher.decryptIfEncrypted("sk-plain")).isEqualTo("sk-plain");
    }
}
