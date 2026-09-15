package com.falapet.auth.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import com.falapet.auth.application.PasswordHasher;

@Component
final class Argon2PasswordHasher implements PasswordHasher {
    private final Argon2PasswordEncoder encoder;

    Argon2PasswordHasher(
            @Value("${falapet.auth.argon2.salt-length:16}") int saltLength,
            @Value("${falapet.auth.argon2.hash-length:32}") int hashLength,
            @Value("${falapet.auth.argon2.parallelism:1}") int parallelism,
            @Value("${falapet.auth.argon2.memory-kib:19456}") int memoryKiB,
            @Value("${falapet.auth.argon2.iterations:2}") int iterations) {
        if (saltLength < 16 || hashLength < 32 || parallelism < 1 || memoryKiB < 19456 || iterations < 2) {
            throw new IllegalStateException("Argon2id parameters are below the safe minimum");
        }
        encoder = new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memoryKiB, iterations);
    }

    @Override
    public String encode(String password) {
        return encoder.encode(password);
    }

    @Override
    public boolean matches(String password, String encoded) {
        return encoder.matches(password, encoded);
    }
}
