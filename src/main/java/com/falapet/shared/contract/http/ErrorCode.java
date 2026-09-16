package com.falapet.shared.contract.http;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, false),
	INVALID_CURSOR(HttpStatus.BAD_REQUEST, false),
	INVALID_STATE(HttpStatus.BAD_REQUEST, false),
	RECOVERY_TOKEN_INVALID(HttpStatus.BAD_REQUEST, false),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, false),
	SESSION_INVALID(HttpStatus.UNAUTHORIZED, false),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, false),
	FORBIDDEN(HttpStatus.FORBIDDEN, false),
	CAPABILITY_DISABLED(HttpStatus.FORBIDDEN, false),
	GOOGLE_LOGIN_UNAVAILABLE(HttpStatus.FORBIDDEN, false),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, false),
	VERSION_CONFLICT(HttpStatus.CONFLICT, false),
	DUPLICATE_RESOURCE(HttpStatus.CONFLICT, false),
	ACTIVE_RESOURCE_CONFLICT(HttpStatus.CONFLICT, false),
	EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, false),
	EVENT_ID_CONFLICT(HttpStatus.CONFLICT, false),
	TRAINING_SESSION_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, false),
	UPLOAD_NOT_FOUND(HttpStatus.CONFLICT, false),
	UPLOAD_EXPIRED(HttpStatus.GONE, false),
	PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, false),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, false),
	DOMAIN_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, false),
	UPLOAD_VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, false),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, true),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, true),
	CAPABILITY_NOT_AVAILABLE(HttpStatus.NOT_IMPLEMENTED, false),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, true);

	private final HttpStatus status;
	private final boolean retryable;

	ErrorCode(HttpStatus status, boolean retryable) {
		this.status = status;
		this.retryable = retryable;
	}

	public HttpStatus status() {
		return status;
	}

	public boolean retryable() {
		return retryable;
	}
}
