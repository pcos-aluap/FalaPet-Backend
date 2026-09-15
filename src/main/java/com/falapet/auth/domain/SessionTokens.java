package com.falapet.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record SessionTokens(UUID sessionId, String accessToken, Instant accessTokenExpiresAt,
        String refreshToken, Instant refreshTokenExpiresAt) {
}
