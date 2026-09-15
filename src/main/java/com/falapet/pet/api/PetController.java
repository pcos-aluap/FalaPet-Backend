package com.falapet.pet.api;

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
import com.falapet.pet.application.PetService;
import com.falapet.pet.domain.Pet;
import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.contract.pagination.PageData;

@RestController
@RequestMapping(path = "/api/v1/pets", produces = ApiMediaTypes.APPLICATION_JSON_VALUE)
public final class PetController {
    private final PetService service;

    PetController(PetService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<PetData>> create(Authentication authentication,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody Map<String, Object> body) {
        Pet pet = service.create(tutorId(authentication), key, body);
        return ResponseEntity.status(201).body(ApiResponse.of(new PetData(pet)));
    }

    @GetMapping
    public ApiResponse<PageData<Pet>> list(Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(service.list(tutorId(authentication), status, cursor, limit));
    }

    @GetMapping("/{petId}")
    public ApiResponse<PetData> detail(Authentication authentication, @PathVariable UUID petId) {
        return ApiResponse.of(new PetData(service.find(tutorId(authentication), petId)));
    }

    @PatchMapping("/{petId}")
    public ApiResponse<PetData> update(Authentication authentication, @PathVariable UUID petId,
            @RequestHeader("If-Match") String ifMatch, @RequestBody Map<String, Object> patch) {
        return ApiResponse.of(new PetData(service.update(tutorId(authentication), petId,
                version(ifMatch), patch)));
    }

    @PostMapping("/{petId}/deactivation")
    public ApiResponse<PetData> deactivate(Authentication authentication, @PathVariable UUID petId) {
        return ApiResponse.of(new PetData(service.setStatus(tutorId(authentication), petId, "INACTIVE")));
    }

    @DeleteMapping("/{petId}/deactivation")
    public ApiResponse<PetData> reactivate(Authentication authentication, @PathVariable UUID petId) {
        return ApiResponse.of(new PetData(service.setStatus(tutorId(authentication), petId, "ACTIVE")));
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

    public record PetData(Pet pet) {}
}
