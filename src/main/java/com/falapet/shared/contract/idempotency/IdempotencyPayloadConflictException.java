package com.falapet.shared.contract.idempotency;

/**
 * Internal signal for a key reused with a different semantic payload.
 * A future endpoint must map it to the endpoint-specific contract code; v1.1.0
 * does not define one generic public code for this situation.
 */
public final class IdempotencyPayloadConflictException extends RuntimeException {

	public IdempotencyPayloadConflictException() {
		super("Idempotency key was reused with a different request fingerprint");
	}
}
