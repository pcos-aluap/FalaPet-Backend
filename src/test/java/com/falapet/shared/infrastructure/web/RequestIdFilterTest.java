package com.falapet.shared.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

	private final RequestIdFilter filter = new RequestIdFilter();

	@Test
	void makesRequestIdAvailableDuringRequestAndClearsItAfterwards() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(RequestIdFilter.HEADER_NAME, "request-42");
		MockHttpServletResponse response = new MockHttpServletResponse();
		String[] requestIdDuringChain = new String[1];
		FilterChain chain = (servletRequest, servletResponse) ->
			requestIdDuringChain[0] = MDC.get(RequestIdFilter.MDC_KEY);

		filter.doFilter(request, response, chain);

		assertThat(requestIdDuringChain[0]).isEqualTo("request-42");
		assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("request-42");
		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}
}
