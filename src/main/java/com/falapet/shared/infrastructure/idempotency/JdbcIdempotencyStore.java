package com.falapet.shared.infrastructure.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.falapet.shared.contract.idempotency.IdempotencyClaim;
import com.falapet.shared.contract.idempotency.IdempotencyPayloadConflictException;
import com.falapet.shared.contract.idempotency.IdempotencyStore;
import com.falapet.shared.contract.idempotency.StoredHttpResponse;

@Component
public class JdbcIdempotencyStore implements IdempotencyStore {

	private static final int MAX_RESPONSE_BYTES = 1_048_576;

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;

	public JdbcIdempotencyStore(JdbcTemplate jdbc, DataSource dataSource) {
		this.jdbc = jdbc;
		this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
	}

	@Override
	public IdempotencyClaim claim(
			String operationScope, String subject, String idempotencyKey, String requestFingerprint) {
		validate(operationScope, subject, idempotencyKey, requestFingerprint);
		return transactions.execute(status -> claimInTransaction(
			operationScope, hash(subject), hash(idempotencyKey), requestFingerprint));
	}

	@Override
	public void complete(
			String operationScope,
			String subject,
			String idempotencyKey,
			String requestFingerprint,
			StoredHttpResponse response) {
		validate(operationScope, subject, idempotencyKey, requestFingerprint);
		if (response.body().length > MAX_RESPONSE_BYTES) {
			throw new IllegalArgumentException("Idempotent response exceeds the persistence limit");
		}
		transactions.executeWithoutResult(status -> completeInTransaction(
			operationScope, hash(subject), hash(idempotencyKey), requestFingerprint, response));
	}

	private IdempotencyClaim claimInTransaction(
			String scope, String subjectHash, String keyHash, String requestFingerprint) {
		int inserted = jdbc.update("""
			INSERT INTO http_idempotency_record
				(operation_scope, subject_fingerprint, key_fingerprint, request_fingerprint, state)
			VALUES (?, ?, ?, ?, 'PENDING')
			ON CONFLICT DO NOTHING
			""", scope, subjectHash, keyHash, requestFingerprint);
		if (inserted == 1) {
			return IdempotencyClaim.acquired();
		}

		return jdbc.queryForObject("""
			SELECT request_fingerprint, state, response_status, response_content_type, response_body
			FROM http_idempotency_record
			WHERE operation_scope = ? AND subject_fingerprint = ? AND key_fingerprint = ?
			FOR UPDATE
			""", (result, row) -> {
				if (!requestFingerprint.equals(result.getString("request_fingerprint"))) {
					throw new IdempotencyPayloadConflictException();
				}
				if ("COMPLETED".equals(result.getString("state"))) {
					return IdempotencyClaim.replay(new StoredHttpResponse(
						result.getInt("response_status"),
						result.getString("response_content_type"),
						result.getBytes("response_body")));
				}
				return IdempotencyClaim.inProgress();
			}, scope, subjectHash, keyHash);
	}

	private void completeInTransaction(
			String scope,
			String subjectHash,
			String keyHash,
			String requestFingerprint,
			StoredHttpResponse response) {
		int updated = jdbc.update("""
			UPDATE http_idempotency_record
			SET state = 'COMPLETED', response_status = ?, response_content_type = ?,
				response_body = ?, updated_at = CURRENT_TIMESTAMP
			WHERE operation_scope = ? AND subject_fingerprint = ? AND key_fingerprint = ?
				AND request_fingerprint = ? AND state = 'PENDING'
			""", response.status(), response.contentType(), response.body(),
			scope, subjectHash, keyHash, requestFingerprint);
		if (updated == 0) {
			throw new IllegalStateException("Idempotency claim is absent, divergent, or already completed");
		}
	}

	private void validate(String scope, String subject, String key, String fingerprint) {
		if (scope == null || scope.isBlank() || scope.length() > 120
				|| subject == null || subject.isBlank()
				|| key == null || key.isBlank()
				|| fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("Invalid idempotency input");
		}
	}

	private String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
