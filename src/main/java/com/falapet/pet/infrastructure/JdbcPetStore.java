package com.falapet.pet.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
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

import com.falapet.pet.application.ActivePetFinder;
import com.falapet.pet.application.PetStore;
import com.falapet.pet.domain.Pet;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

@Component
final class JdbcPetStore implements PetStore, ActivePetFinder {
    private static final String COLUMNS = "id, name, species, birth_date, sex, status, created_at, updated_at, version";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    JdbcPetStore(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public Pet create(UUID tutorId, String key, String fingerprint, String name, String species,
            LocalDate birthDate, String sex) {
        return transactions.execute(status -> {
            UUID id = UUID.randomUUID();
            String keyHash = hash(key);
            Instant now = Instant.now();
            // The Pet and its replay marker commit together. The unique key serializes concurrent creates.
            int marker = jdbc.update("""
                    INSERT INTO pet_creation_idempotency(tutor_id, key_hash, request_fingerprint,
                            pet_id, created_name, created_species, created_birth_date,
                            created_sex, pet_created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, tutorId, keyHash, fingerprint, id, name, species, birthDate, sex,
                    OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            if (marker == 0) {
                var replay = jdbc.queryForMap("""
                        SELECT request_fingerprint, pet_id, created_name, created_species,
                            created_birth_date, created_sex, pet_created_at
                        FROM pet_creation_idempotency
                        WHERE tutor_id = ? AND key_hash = ?
                        """, tutorId, keyHash);
                if (!fingerprint.equals(replay.get("request_fingerprint")))
                    throw new ContractException(ErrorCode.DUPLICATE_RESOURCE);
                Instant createdAt = ((java.sql.Timestamp) replay.get("pet_created_at")).toInstant();
                java.sql.Date originalBirth = (java.sql.Date) replay.get("created_birth_date");
                return new Pet((UUID) replay.get("pet_id"), (String) replay.get("created_name"),
                        (String) replay.get("created_species"), null,
                        originalBirth == null ? null : originalBirth.toLocalDate(),
                        (String) replay.get("created_sex"), "ACTIVE", createdAt, createdAt, 1);
            }
            jdbc.update("""
                    INSERT INTO pet(id, tutor_id, name, species, birth_date, sex, status,
                            created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 1)
                    """, id, tutorId, name, species, birthDate, sex,
                    OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            return find(tutorId, id).orElseThrow();
        });
    }

    @Override
    public List<UUID> activePetIds(UUID tutorId) {
        return jdbc.query("SELECT id FROM pet WHERE tutor_id = ? AND status = 'ACTIVE' ORDER BY id",
                (rs, row) -> rs.getObject("id", UUID.class), tutorId);
    }

    @Override
    public Optional<Pet> find(UUID tutorId, UUID petId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM pet WHERE tutor_id = ? AND id = ?",
                (rs, row) -> map(rs), tutorId, petId).stream().findFirst();
    }

    @Override
    public List<Pet> list(UUID tutorId, String status, Instant beforeCreatedAt, UUID beforeId,
            int limit) {
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS)
                .append(" FROM pet WHERE tutor_id = ?");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(tutorId);
        if (!"ALL".equals(status)) {
            sql.append(" AND status = ?");
            args.add(status);
        }
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
    public Optional<Pet> update(UUID tutorId, UUID petId, long expectedVersion, String name,
            String species, LocalDate birthDate, String sex) {
        return jdbc.query("""
                UPDATE pet SET name = ?, species = ?, birth_date = ?, sex = ?,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE tutor_id = ? AND id = ? AND version = ?
                RETURNING id, name, species, birth_date, sex, status, created_at, updated_at, version
                """, (rs, row) -> map(rs), name, species, birthDate, sex, tutorId, petId,
                expectedVersion).stream().findFirst();
    }

    @Override
    public Optional<Pet> setStatus(UUID tutorId, UUID petId, String target) {
        return transactions.execute(transaction -> {
            jdbc.queryForObject("SELECT id FROM pet WHERE tutor_id = ? AND id = ? FOR UPDATE",
                    UUID.class, tutorId, petId);
            jdbc.update("""
                    UPDATE pet SET status = ?, updated_at = CURRENT_TIMESTAMP,
                        version = version + 1
                    WHERE tutor_id = ? AND id = ? AND status <> ?
                    """, target, tutorId, petId, target);
            return find(tutorId, petId);
        });
    }

    private Pet map(ResultSet rs) throws SQLException {
        java.sql.Date birth = rs.getDate("birth_date");
        return new Pet(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("species"), null, birth == null ? null : birth.toLocalDate(),
                rs.getString("sex"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
