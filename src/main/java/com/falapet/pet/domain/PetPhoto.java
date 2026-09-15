package com.falapet.pet.domain;

import java.time.Instant;
import java.util.UUID;

public record PetPhoto(UUID id, UUID petId, String mediaType, long sizeBytes, int width,
        int height, String sha256, Download download, Instant createdAt, long version) {
    public record Download(String url, Instant expiresAt) {}
}
