package com.falapet.shared.contract.idempotency;

import java.util.Objects;
import java.util.Optional;

public record IdempotencyClaim(Status status, StoredHttpResponse storedResponse) {

	public IdempotencyClaim {
		Objects.requireNonNull(status, "status");
		if ((status == Status.REPLAY) != (storedResponse != null)) {
			throw new IllegalArgumentException("A replay claim must contain its stored response");
		}
	}

	public static IdempotencyClaim acquired() {
		return new IdempotencyClaim(Status.ACQUIRED, null);
	}

	public static IdempotencyClaim inProgress() {
		return new IdempotencyClaim(Status.IN_PROGRESS, null);
	}

	public static IdempotencyClaim replay(StoredHttpResponse response) {
		return new IdempotencyClaim(Status.REPLAY, Objects.requireNonNull(response, "response"));
	}

	public Optional<StoredHttpResponse> replayResponse() {
		return Optional.ofNullable(storedResponse);
	}

	public enum Status {
		ACQUIRED,
		IN_PROGRESS,
		REPLAY
	}
}
