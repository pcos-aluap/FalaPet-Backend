package com.falapet.auth.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.falapet.auth.application.AuthService;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.infrastructure.web.ContractErrorWriter;

@Configuration(proxyBeanMethods = false)
final class AuthSecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain authSecurityFilterChain(HttpSecurity http, AuthService service,
            ContractErrorWriter errorWriter,
            @Qualifier("unmappedApiRequestMatcher") RequestMatcher unmapped) throws Exception {
        http.securityMatcher("/api/v1/**")
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(login -> login.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) ->
                        errorWriter.write(request, response, ErrorCode.SESSION_INVALID.status(), ErrorCode.SESSION_INVALID)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/capabilities").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/google", "/api/v1/auth/refresh",
                                "/api/v1/auth/password-recovery/request",
                                "/api/v1/auth/password-recovery/confirm").permitAll()
                        .requestMatchers(unmapped).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new BearerAuthenticationFilter(service, errorWriter),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
