package com.falapet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.falapet.auth.application.AuthStore;
import com.falapet.auth.domain.Tutor;

@SpringBootTest
class AuthAtomicityIntegrationTest extends PostgreSqlIntegrationTest {
    @Autowired private AuthStore store;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void failedSessionCreationRollsBackTutorAndCredential() {
        Instant now = Instant.now();
        Tutor tutor = new Tutor(UUID.randomUUID(), "Tutor", "rollback-" + UUID.randomUUID()
                + "@example.com", now, 1);
        AuthStore.Issued invalidSession = new AuthStore.Issued(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "a".repeat(64), now, "b".repeat(64), now.plusSeconds(60), now);
        assertThatThrownBy(() -> store.register(tutor, "encoded-password", invalidSession))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_user WHERE id = ?", Integer.class, tutor.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_local_credential WHERE user_id = ?",
                Integer.class, tutor.id())).isZero();
    }
}
