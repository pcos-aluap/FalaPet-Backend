package com.falapet.shared.contract.http;

import java.util.List;
import java.util.Map;

public class ContractException extends RuntimeException {

	private final ErrorCode code;
	private final List<ApiFieldError> fieldErrors;
	private final Map<String, Object> details;

	public ContractException(ErrorCode code) {
		this(code, null, null);
	}

	public ContractException(
			ErrorCode code,
			List<ApiFieldError> fieldErrors,
			Map<String, Object> details) {
		super(code.name());
		this.code = code;
		this.fieldErrors = fieldErrors == null ? null : List.copyOf(fieldErrors);
		this.details = details == null ? null : Map.copyOf(details);
	}

	public ErrorCode code() {
		return code;
	}

	public List<ApiFieldError> fieldErrors() {
		return fieldErrors;
	}

	public Map<String, Object> details() {
		return details;
	}
}
