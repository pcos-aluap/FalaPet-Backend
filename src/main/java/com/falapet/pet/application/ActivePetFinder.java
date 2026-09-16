package com.falapet.pet.application;

import java.util.List;
import java.util.UUID;

/** Explicit read-only boundary used by event ingestion for automatic attribution. */
public interface ActivePetFinder {
    List<UUID> activePetIds(UUID tutorId);
}
