package com.falapet.shared.infrastructure.idempotency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.falapet.shared.contract.idempotency.RequestFingerprint;

import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class IdempotencyConfiguration {

	@Bean
	RequestFingerprint requestFingerprint(ObjectMapper objectMapper) {
		return new RequestFingerprint(objectMapper);
	}
}
