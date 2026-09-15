package com.falapet.user.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.user.domain.DeletionRequest;

@Service
public final class DeletionRequestService {
    private final DeletionRequestGateway gateway;

    DeletionRequestService(DeletionRequestGateway gateway) {
        this.gateway = gateway;
    }

    public DeletionRequest request(UUID tutorId, String key, String body) {
        if (key == null || key.isBlank()) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        return gateway.request(tutorId, key, body == null || body.isBlank() ? "{}" : body);
    }
}
