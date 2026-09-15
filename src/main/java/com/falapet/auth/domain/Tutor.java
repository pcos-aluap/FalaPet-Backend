package com.falapet.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record Tutor(UUID id, String name, String email, Instant createdAt, long version) {
}
