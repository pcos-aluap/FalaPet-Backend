package com.falapet.auth.domain;

import java.util.UUID;

public record AuthenticatedSession(UUID sessionId, Tutor tutor) {
}
