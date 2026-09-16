package com.falapet.button.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.falapet.button.application.ButtonStore;
import com.falapet.button.domain.Button;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

@Component
final class JdbcButtonStore implements ButtonStore {
    private static final String COLUMNS = "id, name, description, status, created_at, updated_at, version";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    JdbcButtonStore(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public Button create(UUID tutorId, String key, String fingerprint, String name, String description) {
        return transactions.execute(status -> {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            int marker = jdbc.update("""
                    INSERT INTO button_creation_idempotency(tutor_id, key_hash, request_fingerprint,
                            button_id, created_name, created_description, button_created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, tutorId, hash(key), fingerprint, id, name, description,
                    OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            if (marker == 0) {
                var replay = jdbc.queryForMap("""
                        SELECT request_fingerprint, button_id, created_name, created_description,
                            button_created_at FROM button_creation_idempotency
                        WHERE tutor_id = ? AND key_hash = ?
                        """, tutorId, hash(key));
                if (!fingerprint.equals(replay.get("request_fingerprint"))) {
                    throw new ContractException(ErrorCode.DUPLICATE_RESOURCE);
                }
                return new Button((UUID) replay.get("button_id"), (String) replay.get("created_name"),
                        (String) replay.get("created_description"), "ACTIVE", null, null,
                        ((java.sql.Timestamp) replay.get("button_created_at")).toInstant(),
                        ((java.sql.Timestamp) replay.get("button_created_at")).toInstant(), 1);
            }
            jdbc.update("""
                    INSERT INTO button(id, tutor_id, name, description, status, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 1)
                    """, id, tutorId, name, description, OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            return find(tutorId, id).orElseThrow();
        });
    }

    @Override
    public Optional<Button> find(UUID tutorId, UUID buttonId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM button WHERE tutor_id = ? AND id = ?",
                (rs, row) -> map(rs), tutorId, buttonId).stream().findFirst();
    }

    @Override
    public List<Button> list(UUID tutorId, String status, Instant beforeCreatedAt, UUID beforeId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM button WHERE tutor_id = ?");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(tutorId);
        if (!"ALL".equals(status)) { sql.append(" AND status = ?"); args.add(status); }
        if (beforeCreatedAt != null) {
            sql.append(" AND (created_at, id) < (?, ?)");
            args.add(OffsetDateTime.ofInstant(beforeCreatedAt, ZoneOffset.UTC));
            args.add(beforeId);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, row) -> map(rs), args.toArray());
    }

    @Override
    public Optional<Button> update(UUID tutorId, UUID buttonId, long expectedVersion, String name,
            String description) {
        return jdbc.query("""
                UPDATE button SET name = ?, description = ?, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE tutor_id = ? AND id = ? AND version = ?
                RETURNING id, name, description, status, created_at, updated_at, version
                """, (rs, row) -> map(rs), name, description, tutorId, buttonId, expectedVersion)
                .stream().findFirst();
    }

    @Override
    public Optional<Button> setStatus(UUID tutorId, UUID buttonId, String target) {
        return transactions.execute(transaction -> {
            jdbc.queryForObject("SELECT id FROM button WHERE tutor_id = ? AND id = ? FOR UPDATE",
                    UUID.class, tutorId, buttonId);
            jdbc.update("""
                    UPDATE button SET status = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1
                    WHERE tutor_id = ? AND id = ? AND status <> ?
                    """, target, tutorId, buttonId, target);
            return find(tutorId, buttonId);
        });
    }

    private Button map(ResultSet rs) throws SQLException {
        return new Button(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("description"), rs.getString("status"), null, null,
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version"));
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
