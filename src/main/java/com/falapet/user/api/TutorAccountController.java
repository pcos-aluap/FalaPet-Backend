package com.falapet.user.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.falapet.auth.TutorIdentity;
import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.user.application.TutorAccountService;
import com.falapet.user.application.DeletionRequestService;
import com.falapet.user.domain.DeletionRequest;
import com.falapet.user.domain.TutorProfile;

@RestController
@RequestMapping(path = "/api/v1/users/me", produces = ApiMediaTypes.APPLICATION_JSON_VALUE)
public final class TutorAccountController {
    private final TutorAccountService service;
    private final DeletionRequestService deletion;

    TutorAccountController(TutorAccountService service, DeletionRequestService deletion) {
        this.service = service;
        this.deletion = deletion;
    }

    @GetMapping
    public ApiResponse<ProfileData> profile(Authentication authentication) {
        return ApiResponse.of(new ProfileData(service.profile(tutorId(authentication))));
    }

    @PatchMapping
    public ApiResponse<ProfileData> update(Authentication authentication,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody java.util.Map<String, Object> patch) {
        long version = parseVersion(ifMatch);
        return ApiResponse.of(new ProfileData(service.updateName(tutorId(authentication), version, patch)));
    }

    @PostMapping("/deletion-requests")
    public org.springframework.http.ResponseEntity<ApiResponse<DeletionRequest>> requestDeletion(
            Authentication authentication, @RequestHeader("Idempotency-Key") String key,
            @RequestBody(required = false) String body) {
        DeletionRequest request = deletion.request(tutorId(authentication), key, body);
        return org.springframework.http.ResponseEntity.accepted().body(ApiResponse.of(request));
    }

    private java.util.UUID tutorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TutorIdentity identity)) {
            throw new ContractException(ErrorCode.SESSION_INVALID);
        }
        return identity.tutorId();
    }

    private long parseVersion(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\"[1-9][0-9]*\"")) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
        } catch (NumberFormatException exception) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
    }

    public record ProfileData(TutorProfile tutor) {}
}
