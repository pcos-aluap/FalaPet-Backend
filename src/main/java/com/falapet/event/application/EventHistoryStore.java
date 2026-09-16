package com.falapet.event.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.falapet.event.domain.EventHistoryItem;
import com.falapet.event.domain.EventMetadata;

public interface EventHistoryStore {
    Optional<EventHistoryItem> find(UUID tutorId, UUID eventId);
    List<EventHistoryItem> list(UUID tutorId, Filters filters, Instant beforeAt, UUID beforeId, int limit);
    Optional<EventMetadata.PetAttribution> replacePetAttribution(UUID tutorId, UUID eventId, UUID petId, long expectedVersion);
    Optional<EventMetadata.PetAttribution> deleteTutorPetAttribution(UUID tutorId, UUID eventId, long expectedVersion);
    Optional<EventMetadata.Classification> replaceClassification(UUID tutorId, UUID eventId, String value, long expectedVersion);
    boolean deleteClassification(UUID tutorId, UUID eventId, long expectedVersion);
    Optional<EventMetadata.Context> replaceContext(UUID tutorId, UUID eventId, UUID contextTypeId, String note, long expectedVersion);
    boolean deleteContext(UUID tutorId, UUID eventId, long expectedVersion);
    boolean petOwnedBy(UUID tutorId, UUID petId);
    boolean activeContextTypeOwnedBy(UUID tutorId, UUID contextTypeId);
    record Filters(Instant from, Instant to, UUID petId, UUID buttonId, String purpose, Boolean training, String classification) {}
}
