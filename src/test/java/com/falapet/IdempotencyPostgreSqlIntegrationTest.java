package com.falapet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.falapet.shared.contract.idempotency.IdempotencyClaim;
import com.falapet.shared.contract.idempotency.IdempotencyPayloadConflictException;
import com.falapet.shared.contract.idempotency.IdempotencyStore;
import com.falapet.shared.contract.idempotency.RequestFingerprint;
import com.falapet.shared.contract.idempotency.StoredHttpResponse;

@SpringBootTest
class IdempotencyPostgreSqlIntegrationTest extends PostgreSqlIntegrationTest {

	@Autowired
	private IdempotencyStore store;

	@Autowired
	private RequestFingerprint fingerprints;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void guaranteesASingleConcurrentClaimAndNeverPersistsRawKeyOrSubject() throws Exception {
		String scope = "contract-test-" + UUID.randomUUID();
		String subject = "tutor-sensitive-" + UUID.randomUUID();
		String key = "key-sensitive-" + UUID.randomUUID();
		String fingerprint = fingerprints.forJson("{\"name\":\"Marley\",\"age\":2}".getBytes(StandardCharsets.UTF_8));
		List<Callable<IdempotencyClaim>> calls = new ArrayList<>();
		for (int index = 0; index < 8; index++) {
			calls.add(() -> store.claim(scope, subject, key, fingerprint));
		}

		List<IdempotencyClaim> claims;
		try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
			claims = executor.invokeAll(calls).stream().map(future -> {
				try {
					return future.get();
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			}).toList();
		}

		assertThat(claims).filteredOn(claim -> claim.status() == IdempotencyClaim.Status.ACQUIRED).hasSize(1);
		assertThat(claims).filteredOn(claim -> claim.status() == IdempotencyClaim.Status.IN_PROGRESS).hasSize(7);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM http_idempotency_record
			WHERE subject_fingerprint = ? OR key_fingerprint = ?
			""", Integer.class, subject, key)).isZero();
	}

	@Test
	void replaysStoredResponseAndRejectsDifferentSemanticPayload() {
		String scope = "replay-test-" + UUID.randomUUID();
		String subject = "subject";
		String key = "key";
		String first = fingerprints.forJson("{\"b\":2, \"a\":1}".getBytes(StandardCharsets.UTF_8));
		String same = fingerprints.forJson("{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8));
		String different = fingerprints.forJson("{\"a\":1,\"b\":3}".getBytes(StandardCharsets.UTF_8));
		StoredHttpResponse response = new StoredHttpResponse(
			201, "application/json;charset=UTF-8", "{\"data\":{}}".getBytes(StandardCharsets.UTF_8));

		assertThat(first).isEqualTo(same).isNotEqualTo(different);
		assertThat(store.claim(scope, subject, key, first).status()).isEqualTo(IdempotencyClaim.Status.ACQUIRED);
		store.complete(scope, subject, key, first, response);
		IdempotencyClaim replay = store.claim(scope, subject, key, same);

		assertThat(replay.status()).isEqualTo(IdempotencyClaim.Status.REPLAY);
		StoredHttpResponse replayed = replay.replayResponse().orElseThrow();
		assertThat(replayed.status()).isEqualTo(response.status());
		assertThat(replayed.contentType()).isEqualTo(response.contentType());
		assertThat(replayed.body()).containsExactly(response.body());
		assertThatThrownBy(() -> store.claim(scope, subject, key, different))
			.isInstanceOf(IdempotencyPayloadConflictException.class);
	}
}
