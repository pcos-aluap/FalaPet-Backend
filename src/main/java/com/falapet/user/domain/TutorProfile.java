package com.falapet.user.domain;

import java.time.Instant;
import java.util.UUID;

public record TutorProfile(UUID id, String name, String email, Instant createdAt, long version) {
}
