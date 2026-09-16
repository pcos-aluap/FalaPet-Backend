package com.falapet.button.domain;

import java.time.Instant;
import java.util.UUID;

/** Logical button. Audio and physical binding are deliberately not managed here. */
public record Button(UUID id, String name, String description, String status, Object activeAudio,
        Object activeBinding, Instant createdAt, Instant updatedAt, long version) {
}
