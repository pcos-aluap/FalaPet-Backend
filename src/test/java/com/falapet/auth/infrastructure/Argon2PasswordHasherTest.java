package com.falapet.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Argon2PasswordHasherTest {
    @Test
    void argon2idUsesIndividualSaltAndVerifiesWithoutExposingPassword() {
        var hasher = new Argon2PasswordHasher(16, 32, 1, 19456, 2);
        String first = hasher.encode("same-long-password-123");
        String second = hasher.encode("same-long-password-123");
        assertThat(first).startsWith("$argon2id$").isNotEqualTo(second);
        assertThat(hasher.matches("same-long-password-123", first)).isTrue();
        assertThat(hasher.matches("different-long-password", first)).isFalse();
    }
}
