package com.falapet.shared.infrastructure.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER_NAME = "X-Request-Id";
	public static final String MDC_KEY = "requestId";

	private static final int MAX_LENGTH = 128;
	private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
	private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String requestId = normalize(request.getHeader(HEADER_NAME));
		long startedAt = System.nanoTime();
		response.setHeader(HEADER_NAME, requestId);
		MDC.put(MDC_KEY, requestId);
		try {
			filterChain.doFilter(request, response);
		} finally {
			String route = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
			String errorCode = (String) request.getAttribute(ContractErrorWriter.ERROR_CODE_ATTRIBUTE);
			LOGGER.atInfo()
				.addKeyValue("httpMethod", request.getMethod())
				.addKeyValue("route", route == null ? request.getRequestURI() : route)
				.addKeyValue("status", response.getStatus())
				.addKeyValue("latencyMs", (System.nanoTime() - startedAt) / 1_000_000)
				.addKeyValue("errorCode", errorCode)
				.log("HTTP request completed");
			MDC.remove(MDC_KEY);
		}
	}

	private String normalize(String candidate) {
		if (candidate != null && candidate.length() <= MAX_LENGTH && VALID_REQUEST_ID.matcher(candidate).matches()) {
			return candidate;
		}
		return UUID.randomUUID().toString();
	}
}
