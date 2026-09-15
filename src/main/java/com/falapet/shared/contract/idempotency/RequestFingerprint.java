package com.falapet.shared.contract.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

public final class RequestFingerprint {

	private final ObjectMapper objectMapper;

	public RequestFingerprint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public String forJson(byte[] json) {
		try {
			Object value = objectMapper.readValue(json, Object.class);
			byte[] canonical = objectMapper.writeValueAsBytes(canonicalize(value));
			return sha256(canonical);
		} catch (JacksonException exception) {
			throw new IllegalArgumentException("Request body is not valid JSON", exception);
		}
	}

	public String forText(String value) {
		return sha256(value.getBytes(StandardCharsets.UTF_8));
	}

	private Object canonicalize(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> sorted = new TreeMap<>();
			map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
			return sorted;
		}
		if (value instanceof List<?> list) {
			List<Object> canonical = new ArrayList<>(list.size());
			list.forEach(item -> canonical.add(canonicalize(item)));
			return canonical;
		}
		return value;
	}

	private String sha256(byte[] value) {
		try {
			return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
