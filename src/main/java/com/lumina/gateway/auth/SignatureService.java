package com.lumina.gateway.auth;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * AppKey HMAC-SHA256 签名服务。
 * <p>
 * 签名串：METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE
 * （TIMESTAMP 为 Unix 秒级时间戳）
 * 阶段 1 未绑定请求体；如需绑定 body，可在后续阶段扩展（需在 WebFilter 中缓存 body）。
 */
@Component
public class SignatureService {

    public String sign(String method, String path, String timestamp, String nonce, String secret) {
        String stringToSign = method + "\n" + path + "\n" + timestamp + "\n" + nonce;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    public boolean verify(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
