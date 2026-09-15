package com.falapet.user.application;

import java.util.Optional;
import java.util.UUID;

import com.falapet.user.domain.DeletionRequest;
import com.falapet.user.domain.TutorProfile;

public interface TutorAccountStore {
    Optional<TutorProfile> find(UUID tutorId);
    Optional<TutorProfile> updateName(UUID tutorId, long expectedVersion, String name);
    DeletionRequest findOrCreateDeletionRequest(UUID tutorId);
}
