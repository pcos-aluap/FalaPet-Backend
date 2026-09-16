package com.falapet.device.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.falapet.device.application.MobileDeviceStore;
import com.falapet.device.domain.MobileDevicePreference;

@Component
final class JdbcMobileDeviceStore implements MobileDeviceStore {
    private final JdbcTemplate jdbc;

    JdbcMobileDeviceStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean register(UUID tutorId, UUID deviceId, String platform, String appVersion) {
        return !jdbc.query("""
                INSERT INTO mobile_device(id, tutor_id, platform, app_version, local_playback_enabled,
                        preference_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO UPDATE SET platform = EXCLUDED.platform,
                    app_version = EXCLUDED.app_version, updated_at = CURRENT_TIMESTAMP
                WHERE mobile_device.tutor_id = EXCLUDED.tutor_id
                RETURNING id
                """, (rs, row) -> rs.getObject("id", UUID.class), deviceId, tutorId, platform,
                appVersion).isEmpty();
    }

    @Override
    public Optional<MobileDevicePreference> findPreference(UUID tutorId, UUID deviceId) {
        return jdbc.query("""
                SELECT local_playback_enabled, preference_version FROM mobile_device
                WHERE tutor_id = ? AND id = ?
                """, (rs, row) -> preference(rs), tutorId, deviceId).stream().findFirst();
    }

    @Override
    public Optional<MobileDevicePreference> updatePreference(UUID tutorId, UUID deviceId,
            long expectedVersion, boolean localPlaybackEnabled) {
        return jdbc.query("""
                UPDATE mobile_device SET local_playback_enabled = ?, preference_version = preference_version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE tutor_id = ? AND id = ? AND preference_version = ?
                RETURNING local_playback_enabled, preference_version
                """, (rs, row) -> preference(rs), localPlaybackEnabled, tutorId, deviceId,
                expectedVersion).stream().findFirst();
    }

    private MobileDevicePreference preference(ResultSet rs) throws SQLException {
        return new MobileDevicePreference(rs.getBoolean("local_playback_enabled"),
                rs.getLong("preference_version"));
    }
}
