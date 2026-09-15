package com.falapet.auth.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class CryptographicTokens {
    private final SecureRandom random = new SecureRandom();
    private final byte[] key;

    public CryptographicTokens(@Value("${falapet.auth.token-hash-key}") String key) {
        this.key = key.getBytes(StandardCharsets.UTF_8);
        if (this.key.length < 32) {
            throw new IllegalStateException("Authentication token hashing key must have at least 32 bytes");
        }
    }

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Authentication hashing unavailable", exception);
        }
    }
}
