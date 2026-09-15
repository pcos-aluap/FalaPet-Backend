package com.falapet.shared.infrastructure.security;

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.infrastructure.web.ContractErrorWriter;

@Configuration(proxyBeanMethods = false)
public class FoundationSecurityConfiguration {

	@Bean
	@Order(2)
	SecurityFilterChain foundationSecurityFilterChain(
			HttpSecurity http,
			ContractErrorWriter errorWriter,
			RequestMatcher unmappedApiRequestMatcher) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.httpBasic(httpBasic -> httpBasic.disable())
			.formLogin(formLogin -> formLogin.disable())
			.logout(logout -> logout.disable())
			.requestCache(requestCache -> requestCache.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) ->
					errorWriter.write(request, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN))
				.accessDeniedHandler((request, response, exception) ->
					errorWriter.write(request, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN)))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/auth/capabilities").permitAll()
				.requestMatchers(unmappedApiRequestMatcher).permitAll()
				.anyRequest().denyAll());

		return http.build();
	}

	@Bean
	RequestMatcher unmappedApiRequestMatcher(
			@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
		return request -> {
			if (!request.getRequestURI().startsWith("/api/v1/")) {
				return false;
			}
			try {
				HandlerExecutionChain chain = handlerMapping.getHandler(request);
				return chain == null || !(chain.getHandler() instanceof HandlerMethod);
			} catch (Exception exception) {
				return true;
			}
		};
	}
}
