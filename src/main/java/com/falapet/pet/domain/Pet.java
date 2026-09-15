package com.falapet.pet.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Pet(UUID id, String name, String species, PetPhoto photo, LocalDate birthDate,
        String sex, String status, Instant createdAt, Instant updatedAt, long version) {
}
