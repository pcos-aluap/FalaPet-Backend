package com.falapet.auth.api;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.falapet.auth.application.AuthService;
import com.falapet.auth.domain.AuthenticatedSession;
import com.falapet.auth.domain.SessionTokens;
import com.falapet.auth.domain.Tutor;
import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = ApiMediaTypes.APPLICATION_JSON_VALUE)
final class AuthController {
    private final AuthService service;

    AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    ResponseEntity<ApiResponse<AuthService.AccountSession>> register(
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody RegisterRequest request) {
        requireKey(key);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.of(service.register(request.name(), request.email(), request.password(), key)));
    }

    @PostMapping("/login")
    ResponseEntity<ApiResponse<AuthService.AccountSession>> login(@RequestBody CredentialsRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.of(service.login(request.email(), request.password())));
    }

    @PostMapping("/google")
    ResponseEntity<ApiResponse<AuthService.AccountSession>> google(@RequestBody GoogleRequest request) {
        throw new ContractException(ErrorCode.GOOGLE_LOGIN_UNAVAILABLE);
    }

    @PostMapping("/refresh")
    ResponseEntity<ApiResponse<SessionTokens>> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.of(service.refresh(request.refreshToken())));
    }

    @GetMapping("/session")
    ResponseEntity<ApiResponse<CurrentSession>> session(Authentication authentication) {
        AuthenticatedSession current = current(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.of(new CurrentSession(current.tutor(), current.sessionId())));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestHeader("Idempotency-Key") String key,
            @RequestHeader("Authorization") String authorization,
            Authentication authentication, @RequestBody(required = false) LogoutRequest request) {
        requireKey(key);
        service.logout(current(authentication).sessionId(), authorization.substring(7), key);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/password-recovery/request")
    ResponseEntity<ApiResponse<Accepted>> requestRecovery(@RequestHeader("Idempotency-Key") String key,
            @RequestBody RecoveryRequest request) {
        requireKey(key);
        service.requestRecovery(request.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(ApiResponse.of(new Accepted(true)));
    }

    @PostMapping("/password-recovery/confirm")
    ResponseEntity<Void> confirmRecovery(@RequestBody RecoveryConfirmRequest request) {
        service.confirmRecovery(request.recoveryToken(), request.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private AuthenticatedSession current(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedSession current)) {
            throw new ContractException(ErrorCode.SESSION_INVALID);
        }
        return current;
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
    }

    record RegisterRequest(String name, String email, String password) {}
    record CredentialsRequest(String email, String password) {}
    record GoogleRequest(String idToken) {}
    record RefreshRequest(String refreshToken) {}
    record LogoutRequest(String refreshToken) {}
    record RecoveryRequest(String email) {}
    record RecoveryConfirmRequest(String recoveryToken, String newPassword) {}
    record CurrentSession(Tutor tutor, UUID sessionId) {}
    record Accepted(boolean accepted) {}
}
