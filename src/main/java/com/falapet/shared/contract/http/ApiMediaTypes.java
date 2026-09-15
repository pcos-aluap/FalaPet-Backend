package com.falapet.shared.contract.http;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

public final class ApiMediaTypes {

	public static final String APPLICATION_JSON_VALUE = "application/json;charset=UTF-8";
	public static final MediaType APPLICATION_JSON =
		new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

	private ApiMediaTypes() {
	}
}
