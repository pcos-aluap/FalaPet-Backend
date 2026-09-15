package com.falapet.user.domain;

import java.time.Instant;
import java.util.UUID;

public record DeletionRequest(UUID requestId, String status, Instant requestedAt, Instant effectiveAt) {
    public DeletionRequest {
        if (!"PENDING".equals(status) || effectiveAt != null) {
            throw new IllegalArgumentException("Only a pending technical deletion request is supported");
        }
    }
}
