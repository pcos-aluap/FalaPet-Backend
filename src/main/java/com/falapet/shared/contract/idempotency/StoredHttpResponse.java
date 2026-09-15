package com.falapet.shared.contract.idempotency;

import java.util.Arrays;
import java.util.Objects;

public record StoredHttpResponse(int status, String contentType, byte[] body) {

	public StoredHttpResponse {
		if (status < 100 || status > 599) {
			throw new IllegalArgumentException("Invalid HTTP status");
		}
		Objects.requireNonNull(contentType, "contentType");
		body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
	}

	@Override
	public byte[] body() {
		return Arrays.copyOf(body, body.length);
	}
}
