package com.falapet.auth.infrastructure;

import org.springframework.stereotype.Component;

import com.falapet.auth.application.RecoverySender;

@Component
final class UnconfiguredRecoverySender implements RecoverySender {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public void send(String email, String recoveryToken) {
        throw new IllegalStateException("Recovery delivery is not configured");
    }
}
