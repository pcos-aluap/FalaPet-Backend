package com.falapet.shared.contract.idempotency;

public interface IdempotencyStore {

	IdempotencyClaim claim(
		String operationScope, String subject, String idempotencyKey, String requestFingerprint);

	void complete(
		String operationScope,
		String subject,
		String idempotencyKey,
		String requestFingerprint,
		StoredHttpResponse response);
}
