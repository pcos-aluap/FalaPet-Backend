package com.falapet.shared.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.falapet.shared.contract.pagination.SignedCursorCodec;

@Configuration(proxyBeanMethods = false)
public class CursorConfiguration {

	@Bean
	SignedCursorCodec signedCursorCodec(@Value("${falapet.http.cursor.signing-key}") String signingKey) {
		return new SignedCursorCodec(signingKey);
	}
}
