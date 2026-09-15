package com.falapet.user.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.contract.idempotency.IdempotencyClaim;
import com.falapet.shared.contract.idempotency.IdempotencyPayloadConflictException;
import com.falapet.shared.contract.idempotency.IdempotencyStore;
import com.falapet.shared.contract.idempotency.RequestFingerprint;
import com.falapet.shared.contract.idempotency.StoredHttpResponse;
import com.falapet.user.application.DeletionRequestGateway;
import com.falapet.user.application.TutorAccountStore;
import com.falapet.user.domain.DeletionRequest;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

@Component
final class JdbcDeletionRequestGateway implements DeletionRequestGateway {
    private static final String SCOPE = "user.deletion-request.v1";
    private final TutorAccountStore accounts;
    private final IdempotencyStore idempotency;
    private final RequestFingerprint fingerprints;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;

    JdbcDeletionRequestGateway(TutorAccountStore accounts, IdempotencyStore idempotency,
            RequestFingerprint fingerprints, ObjectMapper json, DataSource dataSource) {
        this.accounts = accounts;
        this.idempotency = idempotency;
        this.fingerprints = fingerprints;
        this.json = json;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public DeletionRequest request(UUID tutorId, String key, String body) {
        String fingerprint;
        try {
            fingerprint = fingerprints.forJson(body.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new ContractException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            return transactions.execute(status -> {
                String subject = tutorId.toString();
                IdempotencyClaim claim = idempotency.claim(SCOPE, subject, key, fingerprint);
                if (claim.status() == IdempotencyClaim.Status.REPLAY) {
                    return json.readValue(claim.storedResponse().body(), DeletionRequest.class);
                }
                if (claim.status() == IdempotencyClaim.Status.IN_PROGRESS) {
                    throw new ContractException(ErrorCode.SERVICE_UNAVAILABLE);
                }
                try {
                    Object payload = json.readValue(body.getBytes(StandardCharsets.UTF_8), Object.class);
                    if (!(payload instanceof Map<?, ?> map) || !map.isEmpty()) {
                        throw new ContractException(ErrorCode.VALIDATION_ERROR);
                    }
                } catch (JacksonException exception) {
                    throw new ContractException(ErrorCode.VALIDATION_ERROR);
                }
                if (accounts.find(tutorId).isEmpty()) {
                    throw new ContractException(ErrorCode.RESOURCE_NOT_FOUND);
                }
                DeletionRequest request = accounts.findOrCreateDeletionRequest(tutorId);
                idempotency.complete(SCOPE, subject, key, fingerprint,
                        new StoredHttpResponse(202, ApiMediaTypes.APPLICATION_JSON_VALUE,
                                json.writeValueAsBytes(request)));
                return request;
            });
        } catch (IdempotencyPayloadConflictException exception) {
            throw new ContractException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }
}
