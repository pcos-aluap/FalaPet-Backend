package com.falapet.auth.application;

public interface CompromisedPasswordPolicy {
    boolean isCompromised(String password);
}
