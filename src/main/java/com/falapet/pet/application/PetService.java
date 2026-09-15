package com.falapet.pet.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.falapet.pet.domain.Pet;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;
import com.falapet.shared.contract.pagination.CursorPageRequest;
import com.falapet.shared.contract.pagination.CursorState;
import com.falapet.shared.contract.pagination.InvalidCursorException;
import com.falapet.shared.contract.pagination.PageData;
import com.falapet.shared.contract.pagination.PageMetadata;
import com.falapet.shared.contract.pagination.SignedCursorCodec;
import com.falapet.shared.contract.idempotency.RequestFingerprint;

import tools.jackson.databind.ObjectMapper;

@Service
public final class PetService {
    private static final Set<String> FIELDS = Set.of("name", "species", "birthDate", "sex");
    private final PetStore store;
    private final SignedCursorCodec cursors;
    private final RequestFingerprint fingerprints;
    private final ObjectMapper json;

    PetService(PetStore store, SignedCursorCodec cursors, RequestFingerprint fingerprints,
            ObjectMapper json) {
        this.store = store;
        this.cursors = cursors;
        this.fingerprints = fingerprints;
        this.json = json;
    }

    public Pet create(UUID tutorId, String key, Map<String, Object> request) {
        if (key == null || key.isBlank()) throw invalid();
        if (request == null || !FIELDS.containsAll(request.keySet())) throw invalid();
        String name = name(request.get("name"));
        String species = species(request.get("species"));
        LocalDate birthDate = date(request.get("birthDate"));
        String sex = sex(request.get("sex"));
        String fingerprint = fingerprints.forJson(json.writeValueAsBytes(request));
        return store.create(tutorId, key, fingerprint, name, species, birthDate, sex);
    }

    public Pet find(UUID tutorId, UUID petId) {
        return store.find(tutorId, petId)
                .orElseThrow(() -> new ContractException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public PageData<Pet> list(UUID tutorId, String status, String cursor, Integer limit) {
        String filter = status == null || status.isBlank() ? "ACTIVE" : status;
        if (!Set.of("ACTIVE", "INACTIVE", "ALL").contains(filter)) throw invalid();
        CursorPageRequest page = CursorPageRequest.of(cursor, limit);
        Instant beforeAt = null;
        UUID beforeId = null;
        String scope = "pet.list." + fingerprints.forText(tutorId.toString());
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
        List<Pet> rows = store.list(tutorId, filter, beforeAt, beforeId, page.limit() + 1);
        boolean hasMore = rows.size() > page.limit();
        List<Pet> items = hasMore ? rows.subList(0, page.limit()) : rows;
        String next = null;
        if (hasMore) {
            Pet last = items.get(items.size() - 1);
            next = cursors.encode(new CursorState(scope, filter,
                    List.of(last.createdAt().toString(), last.id().toString())));
        }
        return new PageData<>(items, new PageMetadata(next, hasMore));
    }

    public Pet update(UUID tutorId, UUID petId, long expectedVersion, Map<String, Object> patch) {
        if (expectedVersion < 1 || patch == null || patch.isEmpty()
                || !FIELDS.containsAll(patch.keySet())) throw invalid();
        Pet current = find(tutorId, petId);
        if (current.version() != expectedVersion) throw conflict(current);
        String name = patch.containsKey("name") ? name(patch.get("name")) : current.name();
        String species = patch.containsKey("species") ? species(patch.get("species")) : current.species();
        LocalDate birthDate = patch.containsKey("birthDate")
                ? date(patch.get("birthDate")) : current.birthDate();
        String sex = patch.containsKey("sex") ? sex(patch.get("sex")) : current.sex();
        return store.update(tutorId, petId, expectedVersion, name, species, birthDate, sex)
                .orElseGet(() -> { throw conflict(find(tutorId, petId)); });
    }

    public Pet setStatus(UUID tutorId, UUID petId, String status) {
        find(tutorId, petId);
        return store.setStatus(tutorId, petId, status)
                .orElseThrow(() -> new ContractException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ContractException conflict(Pet current) {
        return new ContractException(ErrorCode.VERSION_CONFLICT, null, Map.of("pet", current));
    }

    private ContractException invalid() { return new ContractException(ErrorCode.VALIDATION_ERROR); }

    private String name(Object value) {
        if (!(value instanceof String input)) throw invalid();
        String normalized = input.trim();
        if (normalized.isEmpty() || normalized.codePoints().anyMatch(Character::isISOControl))
            throw invalid();
        return normalized;
    }

    private String species(Object value) {
        if (!(value instanceof String input) || !Set.of("DOG", "CAT", "OTHER").contains(input))
            throw invalid();
        return input;
    }

    private String sex(Object value) {
        if (value == null) return null;
        if (!(value instanceof String input)
                || !Set.of("FEMALE", "MALE", "UNKNOWN").contains(input)) throw invalid();
        return input;
    }

    private LocalDate date(Object value) {
        if (value == null) return null;
        if (!(value instanceof String input) || !input.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw invalid();
        try { return LocalDate.parse(input); }
        catch (DateTimeParseException exception) { throw invalid(); }
    }
}
