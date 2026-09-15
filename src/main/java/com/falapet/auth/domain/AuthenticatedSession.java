package com.falapet.auth.domain;

import java.util.UUID;

public record AuthenticatedSession(UUID sessionId, Tutor tutor) implements com.falapet.auth.TutorIdentity {
    @Override
    public UUID tutorId() {
        return tutor.id();
    }
}
