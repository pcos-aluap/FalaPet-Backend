package com.falapet.auth.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.falapet.auth.application.AuthStore;
import com.falapet.auth.domain.AuthenticatedSession;
import com.falapet.auth.domain.Tutor;

@Component
final class JdbcAuthStore implements AuthStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    JdbcAuthStore(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public Optional<Credentials> credentials(String email) {
        return jdbc.query("""
                SELECT u.id, u.name, u.email_normalized, u.created_at, u.version, c.password_hash
                FROM auth_user u JOIN auth_local_credential c ON c.user_id = u.id
                WHERE u.email_normalized = ?
                """, (rs, row) -> new Credentials(tutor(rs), rs.getString("password_hash")), email)
                .stream().findFirst();
    }

    @Override
    public Optional<Tutor> tutor(String email) {
        return jdbc.query("""
                SELECT id, name, email_normalized, created_at, version
                FROM auth_user WHERE email_normalized = ?
                """, (rs, row) -> tutor(rs), email).stream().findFirst();
    }

    @Override
    public void register(Tutor tutor, String passwordHash, Issued issued) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO auth_user (id, name, email_normalized, created_at, version)
                    VALUES (?, ?, ?, ?, ?)
                    """, tutor.id(), tutor.name(), tutor.email(), utc(tutor.createdAt()), tutor.version());
            jdbc.update("""
                    INSERT INTO auth_local_credential (user_id, password_hash, updated_at)
                    VALUES (?, ?, ?)
                    """, tutor.id(), passwordHash, utc(issued.now()));
            insertSession(tutor.id(), issued);
        });
    }

    @Override
    public void createSession(UUID userId, Issued issued) {
        transactions.executeWithoutResult(status -> insertSession(userId, issued));
    }

    private void insertSession(UUID userId, Issued issued) {
        jdbc.update("""
                INSERT INTO auth_user_session (id, user_id, access_token_hash, access_expires_at, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, issued.sessionId(), userId, issued.accessHash(), utc(issued.accessExpiresAt()), utc(issued.now()));
        jdbc.update("""
                INSERT INTO auth_refresh_family (id, session_id, created_at) VALUES (?, ?, ?)
                """, issued.familyId(), issued.sessionId(), utc(issued.now()));
        jdbc.update("""
                INSERT INTO auth_refresh_token (id, family_id, token_hash, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, issued.tokenId(), issued.familyId(), issued.refreshHash(), utc(issued.now()), utc(issued.refreshExpiresAt()));
    }

    @Override
    public Optional<AuthenticatedSession> findAccess(String accessHash, Instant now) {
        return jdbc.query("""
                SELECT s.id AS session_id, u.id, u.name, u.email_normalized, u.created_at, u.version
                FROM auth_user_session s JOIN auth_user u ON u.id = s.user_id
                WHERE s.access_token_hash = ? AND s.revoked_at IS NULL AND s.access_expires_at > ?
                """, (rs, row) -> new AuthenticatedSession(
                        rs.getObject("session_id", UUID.class), tutor(rs)), accessHash, utc(now))
                .stream().findFirst();
    }

    @Override
    public boolean accessExpired(String accessHash, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM auth_user_session
                    WHERE access_token_hash = ? AND revoked_at IS NULL AND access_expires_at <= ?
                )
                """, Boolean.class, accessHash, utc(now)));
    }

    @Override
    public Rotation rotate(String oldRefreshHash, String newRefreshHash, String newAccessHash,
            Instant newAccessExpiresAt, Instant newRefreshExpiresAt, UUID newTokenId, Instant now) {
        return transactions.execute(status -> {
            var matches = jdbc.query("""
                    SELECT t.id, t.family_id, t.expires_at, t.consumed_at,
                           f.session_id, f.revoked_at AS family_revoked_at,
                           s.revoked_at AS session_revoked_at
                    FROM auth_refresh_token t
                    JOIN auth_refresh_family f ON f.id = t.family_id
                    JOIN auth_user_session s ON s.id = f.session_id
                    WHERE t.token_hash = ?
                    FOR UPDATE OF t, f, s
                    """, (rs, row) -> new RefreshRow(
                            rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class),
                            rs.getTimestamp("expires_at").toInstant(), timestamp(rs, "consumed_at"),
                            rs.getObject("session_id", UUID.class), timestamp(rs, "family_revoked_at"),
                            timestamp(rs, "session_revoked_at")), oldRefreshHash);
            if (matches.isEmpty()) {
                return new Rotation(RotationStatus.INVALID, null);
            }
            RefreshRow old = matches.getFirst();
            if (old.consumedAt() != null) {
                jdbc.update("UPDATE auth_refresh_family SET revoked_at = COALESCE(revoked_at, ?) WHERE id = ?",
                        utc(now), old.familyId());
                jdbc.update("UPDATE auth_user_session SET revoked_at = COALESCE(revoked_at, ?) WHERE id = ?",
                        utc(now), old.sessionId());
                return new Rotation(RotationStatus.REUSED, old.sessionId());
            }
            if (old.expiresAt().isBefore(now) || old.expiresAt().equals(now)
                    || old.familyRevokedAt() != null || old.sessionRevokedAt() != null) {
                return new Rotation(RotationStatus.INVALID, null);
            }
            jdbc.update("UPDATE auth_refresh_token SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL",
                    utc(now), old.id());
            jdbc.update("""
                    INSERT INTO auth_refresh_token (id, family_id, token_hash, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, newTokenId, old.familyId(), newRefreshHash, utc(now), utc(newRefreshExpiresAt));
            jdbc.update("""
                    UPDATE auth_user_session SET access_token_hash = ?, access_expires_at = ? WHERE id = ?
                    """, newAccessHash, utc(newAccessExpiresAt), old.sessionId());
            return new Rotation(RotationStatus.SUCCESS, old.sessionId());
        });
    }

    @Override
    public void revokeSession(UUID sessionId, Instant now) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE auth_user_session SET revoked_at = COALESCE(revoked_at, ?) WHERE id = ?",
                    utc(now), sessionId);
            jdbc.update("UPDATE auth_refresh_family SET revoked_at = COALESCE(revoked_at, ?) WHERE session_id = ?",
                    utc(now), sessionId);
        });
    }

    @Override
    public void logout(UUID sessionId, String accessHash, String keyHash, Instant now) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO auth_logout_idempotency
                        (access_token_hash, key_hash, session_id, completed_at)
                    VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, accessHash, keyHash, sessionId, utc(now));
            jdbc.update("UPDATE auth_user_session SET revoked_at = COALESCE(revoked_at, ?) WHERE id = ?",
                    utc(now), sessionId);
            jdbc.update("UPDATE auth_refresh_family SET revoked_at = COALESCE(revoked_at, ?) WHERE session_id = ?",
                    utc(now), sessionId);
        });
    }

    @Override
    public boolean logoutReplayed(String accessHash, String keyHash) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM auth_logout_idempotency
                    WHERE access_token_hash = ? AND key_hash = ?)
                """, Boolean.class, accessHash, keyHash));
    }

    @Override
    public void createRecovery(UUID userId, UUID tokenId, String tokenHash, Instant now, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO auth_recovery_token (id, user_id, token_hash, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, tokenId, userId, tokenHash, utc(now), utc(expiresAt));
    }

    @Override
    public boolean consumeRecovery(String tokenHash, String newPasswordHash, Instant now) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            var matches = jdbc.query("""
                    SELECT id, user_id, expires_at, consumed_at
                    FROM auth_recovery_token WHERE token_hash = ? FOR UPDATE
                    """, (rs, row) -> new RecoveryRow(rs.getObject("id", UUID.class),
                            rs.getObject("user_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
                            timestamp(rs, "consumed_at")), tokenHash);
            if (matches.isEmpty()) {
                return false;
            }
            RecoveryRow token = matches.getFirst();
            if (token.consumedAt() != null || !token.expiresAt().isAfter(now)) {
                return false;
            }
            jdbc.update("UPDATE auth_local_credential SET password_hash = ?, updated_at = ? WHERE user_id = ?",
                    newPasswordHash, utc(now), token.userId());
            jdbc.update("UPDATE auth_recovery_token SET consumed_at = ? WHERE id = ?", utc(now), token.id());
            jdbc.update("UPDATE auth_user_session SET revoked_at = COALESCE(revoked_at, ?) WHERE user_id = ?",
                    utc(now), token.userId());
            jdbc.update("""
                    UPDATE auth_refresh_family SET revoked_at = COALESCE(revoked_at, ?)
                    WHERE session_id IN (SELECT id FROM auth_user_session WHERE user_id = ?)
                    """, utc(now), token.userId());
            return true;
        }));
    }

    private Tutor tutor(ResultSet rs) throws SQLException {
        return new Tutor(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("email_normalized"), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    private Instant timestamp(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record RefreshRow(UUID id, UUID familyId, Instant expiresAt, Instant consumedAt,
            UUID sessionId, Instant familyRevokedAt, Instant sessionRevokedAt) {}
    private record RecoveryRow(UUID id, UUID userId, Instant expiresAt, Instant consumedAt) {}
}
