package com.falapet.auth.application;

import java.util.function.Supplier;

public interface IdempotentRegistration {
    AuthService.AccountSession execute(String email, String key, String requestFingerprint,
            Supplier<AuthService.AccountSession> registration);
}
