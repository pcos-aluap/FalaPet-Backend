package com.falapet.button.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.falapet.button.domain.Button;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.contract.idempotency.RequestFingerprint;
import com.falapet.shared.contract.pagination.CursorPageRequest;
import com.falapet.shared.contract.pagination.CursorState;
import com.falapet.shared.contract.pagination.InvalidCursorException;
import com.falapet.shared.contract.pagination.PageData;
import com.falapet.shared.contract.pagination.PageMetadata;
import com.falapet.shared.contract.pagination.SignedCursorCodec;

import tools.jackson.databind.ObjectMapper;

@Service
public final class ButtonService {
    private static final Set<String> FIELDS = Set.of("name", "description");
    private final ButtonStore store;
    private final SignedCursorCodec cursors;
    private final RequestFingerprint fingerprints;
    private final ObjectMapper json;

    ButtonService(ButtonStore store, SignedCursorCodec cursors, RequestFingerprint fingerprints,
            ObjectMapper json) {
        this.store = store;
        this.cursors = cursors;
        this.fingerprints = fingerprints;
        this.json = json;
    }

    public Button create(UUID tutorId, String key, Map<String, Object> request) {
        if (key == null || key.isBlank() || request == null || !request.containsKey("name")
                || !FIELDS.containsAll(request.keySet())) throw invalid();
        String name = name(request.get("name"));
        String description = description(request.get("description"));
        return store.create(tutorId, key, fingerprints.forJson(json.writeValueAsBytes(request)), name,
                description);
    }

    public Button find(UUID tutorId, UUID buttonId) {
        return store.find(tutorId, buttonId).orElseThrow(() -> new ContractException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public PageData<Button> list(UUID tutorId, String status, String cursor, Integer limit) {
        String filter = status == null || status.isBlank() ? "ACTIVE" : status;
        if (!Set.of("ACTIVE", "INACTIVE", "ALL").contains(filter)) throw invalid();
        CursorPageRequest page = CursorPageRequest.of(cursor, limit);
        Instant beforeAt = null;
        UUID beforeId = null;
        String scope = "button.list." + fingerprints.forText(tutorId.toString());
        if (cursor != null && !cursor.isBlank()) {
            try {
                CursorState state = cursors.decode(cursor);
                if (!scope.equals(state.scope()) || !filter.equals(state.filterFingerprint())
                        || state.position().size() != 2) throw new InvalidCursorException();
                beforeAt = Instant.parse(state.position().get(0));
                beforeId = UUID.fromString(state.position().get(1));
            } catch (IllegalArgumentException exception) {
                throw new ContractException(ErrorCode.INVALID_CURSOR);
            }
        }
        List<Button> rows = store.list(tutorId, filter, beforeAt, beforeId, page.limit() + 1);
        boolean hasMore = rows.size() > page.limit();
        List<Button> items = hasMore ? rows.subList(0, page.limit()) : rows;
        String next = null;
        if (hasMore) {
            Button last = items.get(items.size() - 1);
            next = cursors.encode(new CursorState(scope, filter,
                    List.of(last.createdAt().toString(), last.id().toString())));
        }
        return new PageData<>(items, new PageMetadata(next, hasMore));
    }

    public Button update(UUID tutorId, UUID buttonId, long expectedVersion, Map<String, Object> patch) {
        if (expectedVersion < 1 || patch == null || patch.isEmpty() || !FIELDS.containsAll(patch.keySet())) {
            throw invalid();
        }
        Button current = find(tutorId, buttonId);
        if (current.version() != expectedVersion) throw conflict(current);
        String name = patch.containsKey("name") ? name(patch.get("name")) : current.name();
        String description = patch.containsKey("description") ? description(patch.get("description"))
                : current.description();
        return store.update(tutorId, buttonId, expectedVersion, name, description)
                .orElseGet(() -> { throw conflict(find(tutorId, buttonId)); });
    }

    public Button setStatus(UUID tutorId, UUID buttonId, String status) {
        find(tutorId, buttonId);
        return store.setStatus(tutorId, buttonId, status)
                .orElseThrow(() -> new ContractException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String name(Object value) {
        if (!(value instanceof String input)) throw invalid();
        String normalized = input.trim();
        if (normalized.isEmpty() || normalized.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return normalized;
    }

    private String description(Object value) {
        if (value == null) return null;
        if (!(value instanceof String input)) throw invalid();
        String normalized = input.trim();
        if (normalized.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return normalized;
    }

    private ContractException invalid() { return new ContractException(ErrorCode.VALIDATION_ERROR); }
    private ContractException conflict(Button current) {
        return new ContractException(ErrorCode.VERSION_CONFLICT, null, Map.of("button", current));
    }
}
