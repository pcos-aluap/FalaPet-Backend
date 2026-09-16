package com.falapet.event.domain;

import java.time.Instant;
import java.util.UUID;

/** Human observations deliberately kept outside the immutable ButtonEvent fact. */
public record EventMetadata(PetAttribution petAttribution, Classification classification, Context context) {
    public record PetAttribution(UUID petId, String source, Instant updatedAt, long version) {}
    public record Classification(String value, Instant updatedAt, long version) {}
    public record Context(UUID contextTypeId, String contextTypeNameSnapshot, String note, Instant updatedAt, long version) {}
}
