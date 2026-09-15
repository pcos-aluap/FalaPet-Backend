package com.falapet.auth.infrastructure;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.falapet.auth.application.AuthService;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
final class LogoutReplayFilter extends OncePerRequestFilter {
    private final AuthService service;

    LogoutReplayFilter(AuthService service) {
        this.service = service;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/api/v1/auth/logout".equals(request.getRequestURI())) {
            String authorization = request.getHeader("Authorization");
            String key = request.getHeader("Idempotency-Key");
            if (authorization != null && authorization.startsWith("Bearer ")
                    && service.logoutReplayed(authorization.substring(7), key)) {
                response.setStatus(204);
                response.setHeader("Cache-Control", "no-store");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
