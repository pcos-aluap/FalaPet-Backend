package com.falapet.device.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.falapet.device.domain.MobileDevicePreference;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

@Service
public final class MobileDeviceService {
    private static final Set<String> REGISTRATION_FIELDS =
            Set.of("platform", "appVersion", "localPlaybackEnabled");
    private static final Set<String> PREFERENCE_FIELDS = Set.of("localPlaybackEnabled");
    private final MobileDeviceStore store;

    MobileDeviceService(MobileDeviceStore store) {
        this.store = store;
    }

    public void register(UUID tutorId, UUID deviceId, Map<String, Object> request) {
        if (request == null || !REGISTRATION_FIELDS.equals(request.keySet())
                || !(request.get("platform") instanceof String platform)
                || !"ANDROID".equals(platform)
                || !(request.get("appVersion") instanceof String appVersion)
                || !(request.get("localPlaybackEnabled") instanceof Boolean initiallyDisabled)
                || initiallyDisabled) {
            throw invalid();
        }
        String normalizedVersion = appVersion.trim();
        if (normalizedVersion.isEmpty() || normalizedVersion.length() > 120
                || normalizedVersion.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        if (!store.register(tutorId, deviceId, platform, normalizedVersion)) throw notFound();
    }

    public MobileDevicePreference preference(UUID tutorId, UUID deviceId) {
        return store.findPreference(tutorId, deviceId).orElseThrow(this::notFound);
    }

    public MobileDevicePreference updatePreference(UUID tutorId, UUID deviceId, long expectedVersion,
            Map<String, Object> request) {
        if (expectedVersion < 1 || request == null || !PREFERENCE_FIELDS.equals(request.keySet())
                || !(request.get("localPlaybackEnabled") instanceof Boolean enabled)) {
            throw invalid();
        }
        MobileDevicePreference current = preference(tutorId, deviceId);
        if (current.version() != expectedVersion) throw conflict(current);
        return store.updatePreference(tutorId, deviceId, expectedVersion, enabled)
                .orElseGet(() -> { throw conflict(preference(tutorId, deviceId)); });
    }

    private ContractException invalid() { return new ContractException(ErrorCode.VALIDATION_ERROR); }
    private ContractException notFound() { return new ContractException(ErrorCode.RESOURCE_NOT_FOUND); }
    private ContractException conflict(MobileDevicePreference current) {
        return new ContractException(ErrorCode.VERSION_CONFLICT, null,
                Map.of("preference", current));
    }
}
