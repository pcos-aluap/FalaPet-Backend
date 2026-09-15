package com.falapet.auth.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.falapet.auth.domain.AuthenticatedSession;
import com.falapet.auth.domain.Tutor;

public interface AuthStore {
    record Credentials(Tutor tutor, String passwordHash) {}
    record Issued(UUID sessionId, UUID familyId, UUID tokenId, String accessHash,
                  Instant accessExpiresAt, String refreshHash, Instant refreshExpiresAt, Instant now) {}
    enum RotationStatus { SUCCESS, INVALID, REUSED }
    record Rotation(RotationStatus status, UUID sessionId) {}

    Optional<Credentials> credentials(String email);
    Optional<Tutor> tutor(String email);
    void register(Tutor tutor, String passwordHash, Issued issued);
    void createSession(UUID userId, Issued issued);
    Optional<AuthenticatedSession> findAccess(String accessHash, Instant now);
    boolean accessExpired(String accessHash, Instant now);
    Rotation rotate(String oldRefreshHash, String newRefreshHash, String newAccessHash,
                    Instant newAccessExpiresAt, Instant newRefreshExpiresAt, UUID newTokenId, Instant now);
    void revokeSession(UUID sessionId, Instant now);
    void logout(UUID sessionId, String accessHash, String keyHash, Instant now);
    boolean logoutReplayed(String accessHash, String keyHash);
    void createRecovery(UUID userId, UUID tokenId, String tokenHash, Instant now, Instant expiresAt);
    boolean consumeRecovery(String tokenHash, String newPasswordHash, Instant now);
}
