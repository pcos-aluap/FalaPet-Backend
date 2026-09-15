package com.falapet.shared.contract.http;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
		ErrorCode code,
		String message,
		boolean retryable,
		List<ApiFieldError> fieldErrors,
		Map<String, Object> details) {
}
