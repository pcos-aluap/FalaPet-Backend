package com.falapet.auth.application;

public interface AuthRateLimiter {
    long retryAfterSeconds(String operation, String subject);
}
