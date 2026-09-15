package com.falapet.auth.application;

public interface PasswordHasher {
    String encode(String password);
    boolean matches(String password, String encoded);
}
