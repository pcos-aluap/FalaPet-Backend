package com.falapet.event.application;

import java.util.Optional;
import java.util.UUID;

public interface EventStore {
    Optional<String> fingerprint(UUID eventId);
    boolean buttonExists(UUID tutorId, UUID buttonId);
    boolean activeEsp32Exists(UUID tutorId, UUID esp32DeviceId);
    boolean bindingMatches(UUID tutorId, UUID esp32DeviceId, String physicalButtonId, UUID buttonId);
    boolean activeTrainingSessionExists(UUID tutorId, UUID sessionId);
    PersistResult persist(UUID tutorId, EventCommand event);

    enum PersistResult { ACCEPTED, ALREADY_ACCEPTED, CONFLICT }
}
