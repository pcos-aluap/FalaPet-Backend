package com.falapet.button.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.falapet.button.domain.Button;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.contract.idempotency.RequestFingerprint;
import com.falapet.shared.contract.pagination.SignedCursorCodec;

import tools.jackson.databind.ObjectMapper;

class ButtonServiceTest {
    private final ButtonService service = new ButtonService(new EmptyStore(),
            new SignedCursorCodec("test-only-cursor-signing-key-32-bytes"),
            new RequestFingerprint(new ObjectMapper()), new ObjectMapper());

    @Test
    void rejectsFieldsThatWouldCoupleLogicalButtonsToPetsAudioOrHardware() {
        UUID tutor = UUID.randomUUID();
        assertValidation(() -> service.create(tutor, "key", Map.of("name", "Food", "petId", "x")));
        assertValidation(() -> service.create(tutor, "key", Map.of("name", "Food", "audioUrl", "x")));
        assertValidation(() -> service.update(tutor, UUID.randomUUID(), 1,
                Map.of("activeBinding", "hardware")));
    }

    private void assertValidation(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ContractException.class)
                .extracting(exception -> ((ContractException) exception).code())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    private static final class EmptyStore implements ButtonStore {
        @Override public Button create(UUID tutorId, String key, String fingerprint, String name, String description) {
            return new Button(UUID.randomUUID(), name, description, "ACTIVE", null, null, Instant.now(), Instant.now(), 1);
        }
        @Override public Optional<Button> find(UUID tutorId, UUID buttonId) { return Optional.empty(); }
        @Override public List<Button> list(UUID tutorId, String status, Instant beforeCreatedAt, UUID beforeId, int limit) { return List.of(); }
        @Override public Optional<Button> update(UUID tutorId, UUID buttonId, long expectedVersion, String name, String description) { return Optional.empty(); }
        @Override public Optional<Button> setStatus(UUID tutorId, UUID buttonId, String status) { return Optional.empty(); }
    }
}
