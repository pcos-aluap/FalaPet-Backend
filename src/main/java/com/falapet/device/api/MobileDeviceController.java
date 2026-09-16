package com.falapet.device.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.falapet.auth.TutorIdentity;
import com.falapet.device.application.MobileDeviceService;
import com.falapet.device.domain.MobileDevicePreference;
import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

@RestController
@RequestMapping(path = "/api/v1/mobile-devices", produces = ApiMediaTypes.APPLICATION_JSON_VALUE)
public final class MobileDeviceController {
    private final MobileDeviceService service;

    MobileDeviceController(MobileDeviceService service) {
        this.service = service;
    }

    @PutMapping("/{mobileDeviceId}")
    public ResponseEntity<Void> register(Authentication authentication, @PathVariable UUID mobileDeviceId,
            @RequestBody Map<String, Object> body) {
        service.register(tutorId(authentication), mobileDeviceId, body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{mobileDeviceId}/preferences")
    public ApiResponse<PreferenceData> preference(Authentication authentication,
            @PathVariable UUID mobileDeviceId) {
        return ApiResponse.of(new PreferenceData(service.preference(tutorId(authentication), mobileDeviceId)));
    }

    @PatchMapping("/{mobileDeviceId}/preferences")
    public ApiResponse<PreferenceData> updatePreference(Authentication authentication,
            @PathVariable UUID mobileDeviceId, @RequestHeader("If-Match") String ifMatch,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.of(new PreferenceData(service.updatePreference(tutorId(authentication),
                mobileDeviceId, version(ifMatch), body)));
    }

    private UUID tutorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TutorIdentity identity)) {
            throw new ContractException(ErrorCode.SESSION_INVALID);
        }
        return identity.tutorId();
    }

    private long version(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\\\"[1-9][0-9]*\\\"")) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        try { return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1)); }
        catch (NumberFormatException exception) { throw new ContractException(ErrorCode.VALIDATION_ERROR); }
    }

    public record PreferenceData(MobileDevicePreference preference) {}
}
