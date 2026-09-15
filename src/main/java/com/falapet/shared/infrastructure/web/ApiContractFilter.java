package com.falapet.shared.infrastructure.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.falapet.shared.contract.http.ErrorCode;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiContractFilter extends OncePerRequestFilter {

	private static final Set<String> METHODS_WITH_OPTIONAL_BODY = Set.of("POST", "PUT", "PATCH");

	private final ContractErrorWriter errorWriter;
	private final long maxJsonPayloadBytes;

	public ApiContractFilter(
			ContractErrorWriter errorWriter,
			@Value("${falapet.http.max-json-payload-bytes:1048576}") long maxJsonPayloadBytes) {
		this.errorWriter = errorWriter;
		this.maxJsonPayloadBytes = maxJsonPayloadBytes;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/v1/");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		long contentLength = request.getContentLengthLong();
		if (contentLength > maxJsonPayloadBytes) {
			errorWriter.write(request, response, HttpStatus.CONTENT_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE);
			return;
		}
		if (hasBody(request, contentLength) && !isJson(request.getContentType())) {
			errorWriter.write(
				request, response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean hasBody(HttpServletRequest request, long contentLength) {
		return METHODS_WITH_OPTIONAL_BODY.contains(request.getMethod()) && contentLength != 0;
	}

	private boolean isJson(String contentType) {
		if (contentType == null) {
			return false;
		}
		try {
			MediaType mediaType = MediaType.parseMediaType(contentType);
			return MediaType.APPLICATION_JSON.getType().equalsIgnoreCase(mediaType.getType())
				&& MediaType.APPLICATION_JSON.getSubtype().equalsIgnoreCase(mediaType.getSubtype())
				&& (mediaType.getCharset() == null || StandardCharsets.UTF_8.equals(mediaType.getCharset()));
		} catch (InvalidMediaTypeException exception) {
			return false;
		}
	}
}
