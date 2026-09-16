package com.falapet.button.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.falapet.button.domain.Button;

public interface ButtonStore {
    Button create(UUID tutorId, String key, String fingerprint, String name, String description);
    Optional<Button> find(UUID tutorId, UUID buttonId);
    List<Button> list(UUID tutorId, String status, Instant beforeCreatedAt, UUID beforeId, int limit);
    Optional<Button> update(UUID tutorId, UUID buttonId, long expectedVersion, String name,
            String description);
    Optional<Button> setStatus(UUID tutorId, UUID buttonId, String status);
}
