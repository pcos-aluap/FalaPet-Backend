package com.falapet.event.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.falapet.shared.contract.http.ErrorCode;

@Service
public final class ButtonEventIngestionService {
    public static final int MAX_BATCH_SIZE = 100;
    private static final Set<String> FIELDS = Set.of("id", "buttonId", "esp32DeviceId", "physicalButtonId",
            "espSessionId", "sequence", "espUptimeMs", "occurredAt", "receivedAt", "timeQuality",
            "transport", "purpose", "trainingSessionId");
    private final EventStore store;

    ButtonEventIngestionService(EventStore store) { this.store = store; }

    public List<Result> ingest(UUID tutorId, List<?> events) {
        if (events == null || events.isEmpty() || events.size() > MAX_BATCH_SIZE) throw new EnvelopeException();
        List<Result> results = new ArrayList<>();
        Set<UUID> seen = new java.util.HashSet<>();
        for (Object raw : events) {
            try {
                EventCommand event = command(raw);
                if (!seen.add(event.id())) { results.add(Result.rejected(event.id(), ErrorCode.VALIDATION_ERROR)); continue; }
                var known = store.fingerprint(event.id());
                if (known.isPresent()) {
                    results.add(known.get().equals(event.fingerprint()) ? Result.already(event.id())
                            : Result.rejected(event.id(), ErrorCode.EVENT_ID_CONFLICT));
                    continue;
                }
                if (!store.buttonExists(tutorId, event.buttonId()) || !store.activeEsp32Exists(tutorId, event.esp32DeviceId())
                        || !store.bindingMatches(tutorId, event.esp32DeviceId(), event.physicalButtonId(), event.buttonId())) {
                    results.add(Result.rejected(event.id(), ErrorCode.RESOURCE_NOT_FOUND)); continue;
                }
                if (event.trainingSessionId() != null && !store.activeTrainingSessionExists(tutorId, event.trainingSessionId())) {
                    results.add(Result.rejected(event.id(), ErrorCode.TRAINING_SESSION_INVALID)); continue;
                }
                results.add(switch (store.persist(tutorId, event)) {
                    case ACCEPTED -> Result.accepted(event.id());
                    case ALREADY_ACCEPTED -> Result.already(event.id());
                    case CONFLICT -> Result.rejected(event.id(), ErrorCode.EVENT_ID_CONFLICT);
                });
            } catch (ItemException exception) { results.add(Result.rejected(exception.eventId, ErrorCode.VALIDATION_ERROR)); }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private EventCommand command(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) throw new ItemException(null);
        Map<String, Object> input = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new ItemException(null);
            input.put(key, entry.getValue());
        }
        UUID id = uuid(input.get("id"));
        if (!input.keySet().equals(FIELDS)) throw new ItemException(id);
        UUID buttonId = uuid(input.get("buttonId"));
        UUID esp32Id = uuid(input.get("esp32DeviceId"));
        String physical = string(input.get("physicalButtonId"));
        UUID espSessionId = uuid(input.get("espSessionId"));
        long sequence = natural(input.get("sequence"));
        long uptime = natural(input.get("espUptimeMs"));
        Instant occurred = utc(input.get("occurredAt"));
        Instant received = utc(input.get("receivedAt"));
        String quality = enumValue(input.get("timeQuality"), Set.of("ESTIMATED_FROM_MOBILE", "RECEIVED_TIME_ONLY"));
        String transport = enumValue(input.get("transport"), Set.of("BLE", "WIFI"));
        String purpose = enumValue(input.get("purpose"), Set.of("BEHAVIORAL", "TEST"));
        UUID training = input.get("trainingSessionId") == null ? null : uuid(input.get("trainingSessionId"));
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("id", id.toString()); canonical.put("buttonId", buttonId.toString()); canonical.put("esp32DeviceId", esp32Id.toString());
        canonical.put("physicalButtonId", physical); canonical.put("espSessionId", espSessionId.toString()); canonical.put("sequence", sequence);
        canonical.put("espUptimeMs", uptime); canonical.put("occurredAt", occurred.toString()); canonical.put("receivedAt", received.toString());
        canonical.put("timeQuality", quality); canonical.put("transport", transport); canonical.put("purpose", purpose);
        canonical.put("trainingSessionId", training == null ? null : training.toString());
        return new EventCommand(id, buttonId, esp32Id, physical, espSessionId, sequence, uptime, occurred, received,
                quality, transport, purpose, training, sha256(canonical.toString()));
    }

    private UUID uuid(Object value) { try { return value instanceof String s ? UUID.fromString(s) : fail(); } catch (IllegalArgumentException ex) { throw new ItemException(null); } }
    private String string(Object value) { if (!(value instanceof String s) || s.isBlank() || !s.equals(s.trim())) throw new ItemException(null); return s; }
    private long natural(Object value) { if (!(value instanceof Number n) || n instanceof Float || n instanceof Double || n.longValue() < 0 || n.doubleValue() != n.longValue()) throw new ItemException(null); return n.longValue(); }
    private Instant utc(Object value) { if (!(value instanceof String s) || !s.endsWith("Z")) throw new ItemException(null); try { return Instant.parse(s); } catch (DateTimeParseException ex) { throw new ItemException(null); } }
    private String enumValue(Object value, Set<String> allowed) { if (!(value instanceof String s) || !allowed.contains(s)) throw new ItemException(null); return s; }
    private UUID fail() { throw new ItemException(null); }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Result(UUID eventId, String status, ItemError error) {
        static Result accepted(UUID id) { return new Result(id, "ACCEPTED", null); }
        static Result already(UUID id) { return new Result(id, "ALREADY_ACCEPTED", null); }
        static Result rejected(UUID id, ErrorCode code) { return new Result(id, "REJECTED", new ItemError(code, message(code))); }
        private static String message(ErrorCode code) { return code == ErrorCode.EVENT_ID_CONFLICT ? "O identificador do evento já foi usado com conteúdo diferente." : "Não foi possível aceitar o evento."; }
    }
    public record ItemError(ErrorCode code, String message) {}
    public static final class EnvelopeException extends RuntimeException {}
    private static final class ItemException extends RuntimeException { private final UUID eventId; ItemException(UUID eventId) { this.eventId = eventId; } }
}
