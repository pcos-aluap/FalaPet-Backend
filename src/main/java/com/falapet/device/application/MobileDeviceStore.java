package com.falapet.device.application;

import java.util.Optional;
import java.util.UUID;

import com.falapet.device.domain.MobileDevicePreference;

public interface MobileDeviceStore {
    boolean register(UUID tutorId, UUID deviceId, String platform, String appVersion);
    Optional<MobileDevicePreference> findPreference(UUID tutorId, UUID deviceId);
    Optional<MobileDevicePreference> updatePreference(UUID tutorId, UUID deviceId,
            long expectedVersion, boolean localPlaybackEnabled);
}
