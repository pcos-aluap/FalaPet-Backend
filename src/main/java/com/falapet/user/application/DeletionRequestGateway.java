package com.falapet.user.application;

import java.util.UUID;

import com.falapet.user.domain.DeletionRequest;

public interface DeletionRequestGateway {
    DeletionRequest request(UUID tutorId, String idempotencyKey, String requestBody);
}
