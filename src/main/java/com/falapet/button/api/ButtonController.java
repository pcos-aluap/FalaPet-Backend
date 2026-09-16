package com.falapet.button.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.falapet.auth.TutorIdentity;
import com.falapet.button.application.ButtonService;
import com.falapet.button.domain.Button;
import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.contract.pagination.PageData;

@RestController
@RequestMapping(path = "/api/v1/buttons", produces = ApiMediaTypes.APPLICATION_JSON_VALUE)
public final class ButtonController {
    private final ButtonService service;

    ButtonController(ButtonService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<ButtonData>> create(Authentication authentication,
            @RequestHeader("Idempotency-Key") String key, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(ApiResponse.of(new ButtonData(service.create(tutorId(authentication), key, body))));
    }

    @GetMapping
    public ApiResponse<PageData<Button>> list(Authentication authentication,
            @RequestParam(required = false) String status, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(service.list(tutorId(authentication), status, cursor, limit));
    }

    @GetMapping("/{buttonId}")
    public ApiResponse<ButtonData> detail(Authentication authentication, @PathVariable UUID buttonId) {
        return ApiResponse.of(new ButtonData(service.find(tutorId(authentication), buttonId)));
    }

    @PatchMapping("/{buttonId}")
    public ApiResponse<ButtonData> update(Authentication authentication, @PathVariable UUID buttonId,
            @RequestHeader("If-Match") String ifMatch, @RequestBody Map<String, Object> patch) {
        return ApiResponse.of(new ButtonData(service.update(tutorId(authentication), buttonId,
                version(ifMatch), patch)));
    }

    @PostMapping("/{buttonId}/deactivation")
    public ApiResponse<ButtonData> deactivate(Authentication authentication, @PathVariable UUID buttonId) {
        return ApiResponse.of(new ButtonData(service.setStatus(tutorId(authentication), buttonId, "INACTIVE")));
    }

    @DeleteMapping("/{buttonId}/deactivation")
    public ApiResponse<ButtonData> reactivate(Authentication authentication, @PathVariable UUID buttonId) {
        return ApiResponse.of(new ButtonData(service.setStatus(tutorId(authentication), buttonId, "ACTIVE")));
    }

    private UUID tutorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TutorIdentity identity))
            throw new ContractException(ErrorCode.SESSION_INVALID);
        return identity.tutorId();
    }

    private long version(String header) {
        if (header == null || !header.matches("\\\"[1-9][0-9]*\\\""))
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        try { return Long.parseLong(header.substring(1, header.length() - 1)); }
        catch (NumberFormatException exception) { throw new ContractException(ErrorCode.VALIDATION_ERROR); }
    }

    public record ButtonData(Button button) {}
}
