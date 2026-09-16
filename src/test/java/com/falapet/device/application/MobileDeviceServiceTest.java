package com.falapet.device.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.falapet.device.domain.MobileDevicePreference;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

class MobileDeviceServiceTest {
    private final MobileDeviceService service = new MobileDeviceService(new EmptyStore());

    @Test
    void rejectsRegistrationThatCouldEnablePlaybackOrAddPrivateFields() {
        UUID tutor = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        assertValidation(() -> service.register(tutor, device,
                Map.of("platform", "ANDROID", "appVersion", "1", "localPlaybackEnabled", true)));
        assertValidation(() -> service.register(tutor, device,
                Map.of("platform", "ANDROID", "appVersion", "1", "localPlaybackEnabled", false,
                        "tutorId", UUID.randomUUID().toString())));
    }

    @Test
    void rejectsPatchFieldsOutsideTheSingleApprovedPreference() {
        assertValidation(() -> service.updatePreference(UUID.randomUUID(), UUID.randomUUID(), 1,
                Map.of("localPlaybackEnabled", false, "playbackStatus", "PLAYED")));
    }

    private void assertValidation(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ContractException.class)
                .extracting(exception -> ((ContractException) exception).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    private static final class EmptyStore implements MobileDeviceStore {
        @Override public boolean register(UUID tutorId, UUID deviceId, String platform, String appVersion) {
            return true;
        }
        @Override public Optional<MobileDevicePreference> findPreference(UUID tutorId, UUID deviceId) {
            return Optional.empty();
        }
        @Override public Optional<MobileDevicePreference> updatePreference(UUID tutorId, UUID deviceId,
                long expectedVersion, boolean localPlaybackEnabled) {
            return Optional.empty();
        }
    }
}
