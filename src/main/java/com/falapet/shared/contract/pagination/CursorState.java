package com.falapet.shared.contract.pagination;

import java.util.List;

public record CursorState(String scope, String filterFingerprint, List<String> position) {

	public CursorState {
		if (scope == null || scope.isBlank()) {
			throw new IllegalArgumentException("Cursor scope is required");
		}
		if (filterFingerprint == null || filterFingerprint.isBlank()) {
			throw new IllegalArgumentException("Cursor filter fingerprint is required");
		}
		position = List.copyOf(position);
	}
}
