package com.falapet.auth.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.falapet.auth.application.CompromisedPasswordPolicy;

@Component
final class Sha256CompromisedPasswordPolicy implements CompromisedPasswordPolicy {
    private final List<byte[]> compromisedHashes;

    Sha256CompromisedPasswordPolicy(
            @Value("${falapet.auth.compromised-password-sha256:}") String configuredHashes) {
        compromisedHashes = configuredHashes.isBlank() ? List.of()
                : java.util.Arrays.stream(configuredHashes.split(","))
                    .map(String::trim)
                    .map(hash -> {
                        if (!hash.matches("[0-9a-fA-F]{64}")) {
                            throw new IllegalStateException("Compromised password policy hash is invalid");
                        }
                        return HexFormat.of().parseHex(hash);
                    }).toList();
    }

    @Override
    public boolean isCompromised(String password) {
        if (compromisedHashes.isEmpty()) {
            return false;
        }
        try {
            byte[] candidate = MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(StandardCharsets.UTF_8));
            boolean compromised = false;
            for (byte[] hash : compromisedHashes) {
                compromised |= MessageDigest.isEqual(candidate, hash);
            }
            return compromised;
        } catch (Exception exception) {
            throw new IllegalStateException("Compromised password policy unavailable", exception);
        }
    }
}
