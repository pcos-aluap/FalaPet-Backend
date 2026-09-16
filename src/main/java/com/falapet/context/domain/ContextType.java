package com.falapet.context.domain;
import java.time.Instant;
import java.util.UUID;
public record ContextType(UUID id, String name, String status, Instant createdAt, Instant updatedAt, long version) {}
