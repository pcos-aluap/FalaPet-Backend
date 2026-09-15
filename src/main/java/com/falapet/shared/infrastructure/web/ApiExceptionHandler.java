package com.falapet.shared.infrastructure.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.falapet.shared.contract.http.ApiErrorResponse;
import com.falapet.shared.contract.http.ApiErrors;
import com.falapet.shared.contract.http.ApiFieldError;
import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ContractException.class)
	ResponseEntity<ApiErrorResponse> handleContractException(
			ContractException exception, HttpServletRequest request) {
		return response(
			exception.code().status(), exception.code(), exception.fieldErrors(), exception.details(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception, HttpServletRequest request) {
		List<ApiFieldError> fields = exception.getBindingResult().getFieldErrors().stream()
			.map(this::toFieldError)
			.toList();
		return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, fields, null, request);
	}

	@ExceptionHandler({
		HttpMessageNotReadableException.class,
		MethodArgumentTypeMismatchException.class,
		HandlerMethodValidationException.class,
		ConstraintViolationException.class
	})
	ResponseEntity<ApiErrorResponse> handleInvalidInput(Exception exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, null, null, request);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ApiErrorResponse> handleMissingParameter(
			MissingServletRequestParameterException exception, HttpServletRequest request) {
		return required(exception.getParameterName(), request);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ApiErrorResponse> handleMissingHeader(
			MissingRequestHeaderException exception, HttpServletRequest request) {
		return required(exception.getHeaderName(), request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> handleUnsupportedMedia(
			HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
		return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE, null, null, request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
			HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
		return response(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.VALIDATION_ERROR, null, null, request);
	}

	@ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
	ResponseEntity<ApiErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, null, null, request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiErrorResponse> handleConflict(
			DataIntegrityViolationException exception, HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE, null, null, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		LOGGER.atError()
			.addKeyValue("exceptionType", exception.getClass().getSimpleName())
			.log("Unhandled HTTP request failure");
		return response(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, null, null, request);
	}

	private ApiFieldError toFieldError(FieldError error) {
		String code = switch (error.getCode() == null ? "" : error.getCode()) {
			case "NotBlank", "NotEmpty", "NotNull" -> "REQUIRED";
			default -> ErrorCode.VALIDATION_ERROR.name();
		};
		return new ApiFieldError(error.getField(), code);
	}

	private ResponseEntity<ApiErrorResponse> required(String field, HttpServletRequest request) {
		return response(
			HttpStatus.BAD_REQUEST,
			ErrorCode.VALIDATION_ERROR,
			List.of(new ApiFieldError(field, "REQUIRED")),
			null,
			request);
	}

	private ResponseEntity<ApiErrorResponse> response(
			HttpStatus status,
			ErrorCode code,
			List<ApiFieldError> fieldErrors,
			java.util.Map<String, Object> details,
			HttpServletRequest request) {
		request.setAttribute(ContractErrorWriter.ERROR_CODE_ATTRIBUTE, code.name());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(ApiMediaTypes.APPLICATION_JSON);
		return new ResponseEntity<>(ApiErrors.of(code, fieldErrors, details), headers, status);
	}
}
