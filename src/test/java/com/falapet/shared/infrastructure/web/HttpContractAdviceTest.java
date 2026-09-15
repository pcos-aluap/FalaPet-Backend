package com.falapet.shared.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;
import com.falapet.shared.contract.pagination.InvalidCursorException;

class HttpContractAdviceTest {

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new ContractTestController())
			.setControllerAdvice(new ApiExceptionHandler())
			.addFilters(new RequestIdFilter())
			.build();
	}

	@Test
	void envelopesSuccessAndValidationErrors() throws Exception {
		mvc.perform(post("/api/v1/_contract/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.header(RequestIdFilter.HEADER_NAME, "validation-1")
				.content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(header().string(RequestIdFilter.HEADER_NAME, "validation-1"))
			.andExpect(content().contentType(ApiMediaTypes.APPLICATION_JSON))
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.error.retryable").value(false))
			.andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"))
			.andExpect(jsonPath("$.error.fieldErrors[0].code").value("REQUIRED"))
			.andExpect(jsonPath("$.error.details").doesNotExist());

		mvc.perform(post("/api/v1/_contract/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Marley\"}"))
			.andExpect(status().isOk())
			.andExpect(content().contentType(ApiMediaTypes.APPLICATION_JSON))
			.andExpect(jsonPath("$.data.name").value("Marley"));
	}

	@Test
	void mapsMalformedJsonAndInvalidCursorWithoutInternalDetails() throws Exception {
		mvc.perform(post("/api/v1/_contract/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{broken"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
			.andExpect(content().string(Matchers.not(Matchers.containsString("Jackson"))));

		mvc.perform(get("/api/v1/_contract/cursor"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
	}

	@Test
	void hidesUnexpectedExceptionAndReturnsSafeInternalError() throws Exception {
		mvc.perform(get("/api/v1/_contract/unexpected"))
			.andExpect(status().isInternalServerError())
			.andExpect(header().exists(RequestIdFilter.HEADER_NAME))
			.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.error.retryable").value(true))
			.andExpect(content().string(Matchers.not(Matchers.containsString("database-password"))))
			.andExpect(content().string(Matchers.not(Matchers.containsString("IllegalStateException"))));
	}

	@RestController
	@RequestMapping(path = "/api/v1/_contract", produces = ApiMediaTypes.APPLICATION_JSON_VALUE)
	private static class ContractTestController {

		@PostMapping(path = "/validation", consumes = MediaType.APPLICATION_JSON_VALUE)
		ApiResponse<NamePayload> validation(@Valid @RequestBody NamePayload payload) {
			return ApiResponse.of(payload);
		}

		@GetMapping("/cursor")
		void cursor() {
			throw new InvalidCursorException();
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("database-password must never be returned");
		}
	}

	private record NamePayload(@NotBlank String name) {
	}
}
