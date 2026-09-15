package com.falapet.user.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.falapet.user.application.TutorAccountStore;
import com.falapet.user.domain.DeletionRequest;
import com.falapet.user.domain.TutorProfile;

@Component
final class JdbcTutorAccountStore implements TutorAccountStore {
    private final JdbcTemplate jdbc;

    JdbcTutorAccountStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TutorProfile> find(UUID tutorId) {
        return jdbc.query("""
                SELECT id, name, email_normalized, created_at, version
                FROM auth_user WHERE id = ?
                """, (rs, row) -> profile(rs), tutorId).stream().findFirst();
    }

    @Override
    public Optional<TutorProfile> updateName(UUID tutorId, long expectedVersion, String name) {
        return jdbc.query("""
                UPDATE auth_user SET name = ?, version = version + 1
                WHERE id = ? AND version = ?
                RETURNING id, name, email_normalized, created_at, version
                """, (rs, row) -> profile(rs), name, tutorId, expectedVersion).stream().findFirst();
    }

    @Override
    public DeletionRequest findOrCreateDeletionRequest(UUID tutorId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO user_deletion_request (id, user_id, status, requested_at)
                VALUES (?, ?, 'PENDING', ?) ON CONFLICT (user_id) DO NOTHING
                """, id, tutorId, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        return jdbc.queryForObject("""
                SELECT id, status, requested_at, effective_at
                FROM user_deletion_request WHERE user_id = ?
                """, (rs, row) -> new DeletionRequest(
                rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getTimestamp("requested_at").toInstant(), null), tutorId);
    }

    private TutorProfile profile(ResultSet rs) throws SQLException {
        return new TutorProfile(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("email_normalized"), rs.getTimestamp("created_at").toInstant(),
                rs.getLong("version"));
    }
}
