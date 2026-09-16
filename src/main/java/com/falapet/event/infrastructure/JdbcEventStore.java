package com.falapet.event.infrastructure;

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

import com.falapet.event.application.EventCommand;
import com.falapet.event.application.EventStore;
import com.falapet.pet.application.ActivePetFinder;

/** PostgreSQL is the source of truth for global event deduplication. */
@Component
final class JdbcEventStore implements EventStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ActivePetFinder pets;

    JdbcEventStore(JdbcTemplate jdbc, DataSource dataSource, ActivePetFinder pets) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.pets = pets;
    }

    @Override public Optional<String> fingerprint(UUID eventId) {
        return jdbc.query("SELECT payload_fingerprint FROM button_event WHERE id = ?", (rs, row) -> rs.getString(1), eventId).stream().findFirst();
    }
    @Override public boolean buttonExists(UUID tutorId, UUID buttonId) {
        return exists("SELECT 1 FROM button WHERE tutor_id = ? AND id = ?", tutorId, buttonId);
    }
    @Override public boolean activeEsp32Exists(UUID tutorId, UUID esp32DeviceId) {
        return exists("SELECT 1 FROM esp32_device WHERE tutor_id = ? AND id = ? AND status = 'ACTIVE'", tutorId, esp32DeviceId);
    }
    @Override public boolean bindingMatches(UUID tutorId, UUID esp32DeviceId, String physicalButtonId, UUID buttonId) {
        return exists("""
                SELECT 1 FROM physical_button_binding binding
                JOIN esp32_device device ON device.id = binding.esp32_device_id
                JOIN button button ON button.id = binding.button_id
                WHERE device.tutor_id = ? AND device.id = ? AND binding.physical_button_id = ?
                  AND binding.button_id = ? AND binding.status = 'ACTIVE' AND button.tutor_id = ?
                """, tutorId, esp32DeviceId, physicalButtonId, buttonId, tutorId);
    }
    @Override public boolean activeTrainingSessionExists(UUID tutorId, UUID sessionId) {
        return exists("SELECT 1 FROM training_session WHERE tutor_id = ? AND id = ? AND status = 'ACTIVE'", tutorId, sessionId);
    }

    @Override public PersistResult persist(UUID tutorId, EventCommand event) {
        return transactions.execute(status -> {
            Instant now = Instant.now();
            int inserted = jdbc.update("""
                    INSERT INTO button_event(id, tutor_id, button_id, esp32_device_id, physical_button_id,
                        esp_session_id, sequence, esp_uptime_ms, occurred_at, received_at, time_quality,
                        transport, purpose, training_session_id, payload_fingerprint, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """, event.id(), tutorId, event.buttonId(), event.esp32DeviceId(), event.physicalButtonId(),
                    event.espSessionId(), event.sequence(), event.espUptimeMs(), utc(event.occurredAt()), utc(event.receivedAt()),
                    event.timeQuality(), event.transport(), event.purpose(), event.trainingSessionId(), event.fingerprint(), utc(now));
            if (inserted == 0) {
                String existing = jdbc.queryForObject("SELECT payload_fingerprint FROM button_event WHERE id = ?", String.class, event.id());
                return existing.equals(event.fingerprint()) ? PersistResult.ALREADY_ACCEPTED : PersistResult.CONFLICT;
            }
            var activePets = pets.activePetIds(tutorId);
            if (activePets.size() == 1) jdbc.update("""
                    INSERT INTO event_pet_attribution(event_id, pet_id, origin, created_at)
                    VALUES (?, ?, 'AUTOMATIC_SINGLE_ACTIVE_PET', ?)
                    """, event.id(), activePets.getFirst(), utc(now));
            return PersistResult.ACCEPTED;
        });
    }

    private boolean exists(String sql, Object... args) { return !jdbc.query(sql, (rs, row) -> 1, args).isEmpty(); }
    private OffsetDateTime utc(Instant instant) { return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC); }
}
