package com.falapet.pet.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.falapet.pet.domain.Pet;

public interface PetStore {
    Pet create(UUID tutorId, String key, String fingerprint, String name, String species,
            LocalDate birthDate, String sex);
    Optional<Pet> find(UUID tutorId, UUID petId);
    List<Pet> list(UUID tutorId, String status, Instant beforeCreatedAt, UUID beforeId, int limit);
    Optional<Pet> update(UUID tutorId, UUID petId, long expectedVersion, String name,
            String species, LocalDate birthDate, String sex);
    Optional<Pet> setStatus(UUID tutorId, UUID petId, String status);
}
