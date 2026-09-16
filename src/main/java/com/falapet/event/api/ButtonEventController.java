package com.falapet.event.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.falapet.auth.TutorIdentity;
import com.falapet.event.application.ButtonEventIngestionService;
import com.falapet.event.application.ButtonEventIngestionService.EnvelopeException;
import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

@RestController
@RequestMapping(path = "/api/v1/sync/button-events", produces = ApiMediaTypes.APPLICATION_JSON_VALUE)
public final class ButtonEventController {
    private final ButtonEventIngestionService service;
    ButtonEventController(ButtonEventIngestionService service) { this.service = service; }

    @PostMapping(consumes = ApiMediaTypes.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ResultsData>> ingest(Authentication authentication,
            @RequestBody Map<String, Object> envelope) {
        if (envelope == null || envelope.size() != 1 || !envelope.containsKey("events") || !(envelope.get("events") instanceof List<?> events)) throw invalid();
        try { return ResponseEntity.ok(ApiResponse.of(new ResultsData(service.ingest(tutorId(authentication), events)))); }
        catch (EnvelopeException exception) { throw invalid(); }
    }

    private UUID tutorId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TutorIdentity identity)) throw new ContractException(ErrorCode.SESSION_INVALID);
        return identity.tutorId();
    }
    private ContractException invalid() { return new ContractException(ErrorCode.VALIDATION_ERROR); }

    public record ResultsData(List<ButtonEventIngestionService.Result> results) {}
}
