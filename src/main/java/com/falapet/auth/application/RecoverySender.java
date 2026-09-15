package com.falapet.auth.application;

public interface RecoverySender {
    boolean available();
    void send(String email, String recoveryToken);
}
