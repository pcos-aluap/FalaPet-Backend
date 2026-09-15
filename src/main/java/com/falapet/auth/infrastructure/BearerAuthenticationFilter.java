package com.falapet.auth.infrastructure;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.falapet.auth.application.AuthService;
import com.falapet.auth.domain.AuthenticatedSession;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.infrastructure.web.ContractErrorWriter;

final class BearerAuthenticationFilter extends OncePerRequestFilter {
    private final AuthService service;
    private final ContractErrorWriter errors;

    BearerAuthenticationFilter(AuthService service, ContractErrorWriter errors) {
        this.service = service;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.startsWith("Bearer ") || header.length() <= 7 || header.indexOf(' ', 7) >= 0) {
                errors.write(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.SESSION_INVALID);
                return;
            }
            try {
                AuthenticatedSession principal = service.authenticate(header.substring(7));
                var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ContractException exception) {
                errors.write(request, response, exception.code().status(), exception.code());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
