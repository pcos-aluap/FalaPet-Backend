package com.falapet.user.application;

import java.util.UUID;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.user.domain.TutorProfile;

@Service
public final class TutorAccountService {
    private final TutorAccountStore store;

    TutorAccountService(TutorAccountStore store) {
        this.store = store;
    }

    public TutorProfile profile(UUID tutorId) {
        return store.find(tutorId).orElseThrow(() -> new ContractException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public TutorProfile updateName(UUID tutorId, long expectedVersion, Map<String, Object> patch) {
        if (patch == null || patch.size() != 1 || !(patch.get("name") instanceof String name)) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        String normalized = normalizeName(name);
        if (expectedVersion < 1) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        return store.updateName(tutorId, expectedVersion, normalized).orElseGet(() -> {
            TutorProfile current = store.find(tutorId)
                    .orElseThrow(() -> new ContractException(ErrorCode.RESOURCE_NOT_FOUND));
            throw new ContractException(ErrorCode.VERSION_CONFLICT, null, Map.of("tutor", current));
        });
    }

    private String normalizeName(String value) {
        if (value == null) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 120 || normalized.codePoints()
                .anyMatch(Character::isISOControl)) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }
}
