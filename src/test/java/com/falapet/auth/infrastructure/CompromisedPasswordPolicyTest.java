package com.falapet.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class CompromisedPasswordPolicyTest {
    @Test
    void configuredHashRejectsOnlyMatchingPasswordWithoutStoringPlaintext() throws Exception {
        String password = "known-compromised-password";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(password.getBytes(StandardCharsets.UTF_8)));
        var policy = new Sha256CompromisedPasswordPolicy(hash);
        assertThat(policy.isCompromised(password)).isTrue();
        assertThat(policy.isCompromised("other-secure-password")).isFalse();
        assertThat(new Sha256CompromisedPasswordPolicy("").isCompromised(password)).isFalse();
    }
}
