package com.falapet.event.domain;

import java.time.Instant;
import java.util.UUID;

public record EventHistoryItem(UUID id, UUID buttonId, String buttonNameSnapshot, UUID esp32DeviceId,
        String physicalButtonId, UUID espSessionId, long sequence, long espUptimeMs, Instant occurredAt,
        Instant receivedAt, String timeQuality, String transport, String purpose, UUID trainingSessionId,
        Instant createdAt, EventMetadata metadata) {}
