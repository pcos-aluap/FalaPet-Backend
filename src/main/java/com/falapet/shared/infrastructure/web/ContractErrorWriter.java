package com.falapet.shared.infrastructure.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import com.falapet.shared.contract.http.ApiErrors;
import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ErrorCode;

@Component
public class ContractErrorWriter {

	public static final String ERROR_CODE_ATTRIBUTE = ContractErrorWriter.class.getName() + ".errorCode";

	private final ObjectMapper objectMapper;

	public ContractErrorWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(
			HttpServletRequest request,
			HttpServletResponse response,
			HttpStatus status,
			ErrorCode code) throws IOException {
		request.setAttribute(ERROR_CODE_ATTRIBUTE, code.name());
		response.setStatus(status.value());
		response.setContentType(ApiMediaTypes.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), ApiErrors.of(code));
	}
}
