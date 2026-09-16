package com.falapet.event.application;

import java.time.Instant;
import java.util.UUID;

public record EventCommand(UUID id, UUID buttonId, UUID esp32DeviceId, String physicalButtonId,
        UUID espSessionId, long sequence, long espUptimeMs, Instant occurredAt, Instant receivedAt,
        String timeQuality, String transport, String purpose, UUID trainingSessionId,
        String fingerprint) {
}
