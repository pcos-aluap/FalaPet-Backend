package com.falapet.auth.infrastructure;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.falapet.auth.application.AuthRateLimiter;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.infrastructure.web.ContractErrorWriter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
final class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Map<String, String> OPERATIONS = Map.of(
            "/api/v1/auth/register", "register",
            "/api/v1/auth/login", "login",
            "/api/v1/auth/refresh", "refresh",
            "/api/v1/auth/password-recovery/request", "recovery-request",
            "/api/v1/auth/password-recovery/confirm", "recovery-confirm");

    private final AuthRateLimiter limiter;
    private final ContractErrorWriter errors;

    AuthRateLimitFilter(AuthRateLimiter limiter, ContractErrorWriter errors) {
        this.limiter = limiter;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String operation = "POST".equals(request.getMethod()) ? OPERATIONS.get(request.getRequestURI()) : null;
        if (operation != null) {
            long retry = limiter.retryAfterSeconds(operation, request.getRemoteAddr());
            if (retry > 0) {
                response.setHeader("Retry-After", Long.toString(retry));
                response.setHeader("Cache-Control", "no-store");
                errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
