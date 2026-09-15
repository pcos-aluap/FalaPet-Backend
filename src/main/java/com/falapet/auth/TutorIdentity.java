package com.falapet.auth;

import java.util.UUID;

/** Minimal internal identity exposed by the authentication module. */
public interface TutorIdentity {
    UUID tutorId();
}
