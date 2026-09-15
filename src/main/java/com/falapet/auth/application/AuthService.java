package com.falapet.auth.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.falapet.auth.domain.AuthenticatedSession;
import com.falapet.auth.domain.EmailAddress;
import com.falapet.auth.domain.SessionTokens;
import com.falapet.auth.domain.Tutor;
import com.falapet.auth.infrastructure.CryptographicTokens;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

@Service
public final class AuthService {
    public record AccountSession(Tutor tutor, SessionTokens session) {}

    private final AuthStore store;
    private final IdempotentRegistration registration;
    private final PasswordHasher passwords;
    private final CompromisedPasswordPolicy compromised;
    private final RecoverySender recoverySender;
    private final CryptographicTokens tokens;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Duration recoveryTtl;
    private final String dummyPasswordHash;

    AuthService(AuthStore store, IdempotentRegistration registration,
            PasswordHasher passwords, CompromisedPasswordPolicy compromised,
            RecoverySender recoverySender, CryptographicTokens tokens,
            @Value("${falapet.auth.access-ttl}") Duration accessTtl,
            @Value("${falapet.auth.refresh-ttl}") Duration refreshTtl,
            @Value("${falapet.auth.recovery-ttl}") Duration recoveryTtl) {
        this.store = store;
        this.registration = registration;
        this.passwords = passwords;
        this.compromised = compromised;
        this.recoverySender = recoverySender;
        this.tokens = tokens;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.recoveryTtl = recoveryTtl;
        if (accessTtl.isNegative() || accessTtl.isZero() || accessTtl.compareTo(Duration.ofMinutes(30)) > 0
                || refreshTtl.compareTo(accessTtl) <= 0 || refreshTtl.compareTo(Duration.ofDays(90)) > 0
                || recoveryTtl.isNegative() || recoveryTtl.isZero() || recoveryTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException("Authentication token TTL configuration is unsafe");
        }
        this.dummyPasswordHash = passwords.encode(tokens.generate());
    }

    public AccountSession register(String name, String email, String password, String idempotencyKey) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = validateName(name);
        validatePassword(password);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        String fingerprint = tokens.hash(lengthPrefix(normalizedName) + lengthPrefix(normalizedEmail)
                + lengthPrefix(password));
        return registration.execute(normalizedEmail, idempotencyKey, fingerprint,
                () -> createRegisteredAccount(normalizedName, normalizedEmail, password));
    }

    private AccountSession createRegisteredAccount(String normalizedName, String normalizedEmail, String password) {
        Instant now = Instant.now();
        Tutor tutor = new Tutor(UUID.randomUUID(), normalizedName, normalizedEmail, now, 1);
        Material material = material(UUID.randomUUID(), now);
        try {
            store.register(tutor, passwords.encode(password), material.issued());
        } catch (DuplicateKeyException exception) {
            throw new ContractException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        return new AccountSession(tutor, material.response());
    }

    private String lengthPrefix(String value) {
        return value.length() + ":" + value;
    }

    public AccountSession login(String email, String password) {
        String normalizedEmail;
        try {
            normalizedEmail = EmailAddress.normalize(email);
        } catch (IllegalArgumentException exception) {
            normalizedEmail = "invalid@example.invalid";
        }
        var found = store.credentials(normalizedEmail);
        String stored = found.map(AuthStore.Credentials::passwordHash).orElse(dummyPasswordHash);
        boolean correct = passwords.matches(password == null ? "" : password, stored);
        if (!correct || found.isEmpty()) {
            throw new ContractException(ErrorCode.INVALID_CREDENTIALS);
        }
        Tutor tutor = found.orElseThrow().tutor();
        Instant now = Instant.now();
        Material material = material(UUID.randomUUID(), now);
        store.createSession(tutor.id(), material.issued());
        return new AccountSession(tutor, material.response());
    }

    public SessionTokens refresh(String refreshToken) {
        if (!validToken(refreshToken)) {
            throw new ContractException(ErrorCode.SESSION_INVALID);
        }
        Instant now = Instant.now();
        String newAccess = tokens.generate();
        String newRefresh = tokens.generate();
        Instant accessExpires = now.plus(accessTtl);
        Instant refreshExpires = now.plus(refreshTtl);
        AuthStore.Rotation rotated = store.rotate(tokens.hash(refreshToken), tokens.hash(newRefresh),
                tokens.hash(newAccess), accessExpires, refreshExpires, UUID.randomUUID(), now);
        if (rotated.status() != AuthStore.RotationStatus.SUCCESS) {
            throw new ContractException(ErrorCode.SESSION_INVALID);
        }
        return new SessionTokens(rotated.sessionId(), newAccess, accessExpires, newRefresh, refreshExpires);
    }

    public AuthenticatedSession authenticate(String accessToken) {
        if (!validToken(accessToken)) {
            throw new ContractException(ErrorCode.SESSION_INVALID);
        }
        String hash = tokens.hash(accessToken);
        Instant now = Instant.now();
        return store.findAccess(hash, now).orElseThrow(() -> new ContractException(
                store.accessExpired(hash, now) ? ErrorCode.TOKEN_EXPIRED : ErrorCode.SESSION_INVALID));
    }

    public void logout(UUID sessionId, String accessToken, String idempotencyKey) {
        store.logout(sessionId, tokens.hash(accessToken), tokens.hash(idempotencyKey), Instant.now());
    }

    public boolean logoutReplayed(String accessToken, String idempotencyKey) {
        return validToken(accessToken) && idempotencyKey != null && !idempotencyKey.isBlank()
                && store.logoutReplayed(tokens.hash(accessToken), tokens.hash(idempotencyKey));
    }

    public void requestRecovery(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!recoverySender.available()) {
            return;
        }
        var tutor = store.tutor(normalizedEmail);
        if (tutor.isPresent()) {
            String token = tokens.generate();
            Instant now = Instant.now();
            store.createRecovery(tutor.orElseThrow().id(), UUID.randomUUID(), tokens.hash(token),
                    now, now.plus(recoveryTtl));
            recoverySender.send(normalizedEmail, token);
        }
    }

    public void confirmRecovery(String recoveryToken, String newPassword) {
        if (!validToken(recoveryToken)) {
            throw new ContractException(ErrorCode.RECOVERY_TOKEN_INVALID);
        }
        validatePassword(newPassword);
        if (!store.consumeRecovery(tokens.hash(recoveryToken), passwords.encode(newPassword), Instant.now())) {
            throw new ContractException(ErrorCode.RECOVERY_TOKEN_INVALID);
        }
    }

    public boolean passwordRecoveryAvailable() {
        return recoverySender.available();
    }

    private Material material(UUID sessionId, Instant now) {
        String access = tokens.generate();
        String refresh = tokens.generate();
        Instant accessExpires = now.plus(accessTtl);
        Instant refreshExpires = now.plus(refreshTtl);
        return new Material(new AuthStore.Issued(sessionId, UUID.randomUUID(), UUID.randomUUID(),
                tokens.hash(access), accessExpires, tokens.hash(refresh), refreshExpires, now),
                new SessionTokens(sessionId, access, accessExpires, refresh, refreshExpires));
    }

    private String normalizeEmail(String email) {
        try {
            return EmailAddress.normalize(email);
        } catch (IllegalArgumentException exception) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        String trimmed = name.trim();
        if (trimmed.length() > 120 || trimmed.codePoints().anyMatch(Character::isISOControl)) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        if (compromised.isCompromised(password)) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private record Material(AuthStore.Issued issued, SessionTokens response) {}
}
