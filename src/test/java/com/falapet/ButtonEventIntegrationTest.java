package com.falapet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "falapet.auth.rate.max-attempts=100")
class ButtonEventIntegrationTest extends PostgreSqlIntegrationTest {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort private int port;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void acceptsPartiallyDeduplicatesAndKeepsRawFactImmutable() throws Exception {
        Tutor tutor = register();
        Fixture fixture = fixture(tutor);
        UUID id = UUID.randomUUID();
        String valid = event(id, fixture.buttonId, fixture.espId, "button-a", 42, null, "BEHAVIORAL");
        String missingButton = event(UUID.randomUUID(), UUID.randomUUID(), fixture.espId, "button-a", 43, null, "BEHAVIORAL");
        HttpResponse<String> mixed = post(tutor.token, "{\"events\":[" + valid + "," + missingButton + "," + valid + "]}");
        assertThat(mixed.statusCode()).isEqualTo(200);
        JsonNode results = json.readTree(mixed.body()).get("data").get("results");
        assertThat(results).hasSize(3);
        assertThat(results.get(0).get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(results.get(1).get("error").get("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(results.get(2).get("error").get("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(post(tutor.token, "{\"events\":[" + valid + "]}").body()).contains("ALREADY_ACCEPTED");
        assertThat(post(tutor.token, "{\"events\":[" + event(id, fixture.buttonId, fixture.espId, "button-a", 99, null, "BEHAVIORAL") + "]}").body()).contains("EVENT_ID_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM button_event WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE button_event SET sequence = 7 WHERE id = ?", id)).isInstanceOf(Exception.class);
    }

    @Test
    void validatesEnvelopeReferencesSessionAndAutomaticAttribution() throws Exception {
        Tutor tutor = register();
        Fixture fixture = fixture(tutor);
        assertThat(post(tutor.token, "{\"events\":[]}").statusCode()).isEqualTo(400);
        StringBuilder oversized = new StringBuilder("{\"events\":[");
        for (int i = 0; i < 101; i++) { if (i > 0) oversized.append(','); oversized.append("{}"); }
        assertThat(post(tutor.token, oversized.append("]}").toString()).statusCode()).isEqualTo(400);
        UUID pet = pet(tutor.token);
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO training_session(id,tutor_id,pet_id,status,started_at,completed_at,note) VALUES (?,?,?,'ACTIVE',?,NULL,NULL)", session, tutor.id, pet, Timestamp.from(Instant.now()));
        UUID event = UUID.randomUUID();
        assertThat(post(tutor.token, "{\"events\":[" + event(event, fixture.buttonId, fixture.espId, "button-a", 1, session, "TEST") + "]}").body()).contains("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_pet_attribution WHERE event_id = ?", Integer.class, event)).isEqualTo(1);
        jdbc.update("UPDATE pet SET status='INACTIVE' WHERE id = ?", pet);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_pet_attribution WHERE event_id = ?", Integer.class, event)).isEqualTo(1);
        UUID completed = UUID.randomUUID();
        jdbc.update("INSERT INTO training_session(id,tutor_id,pet_id,status,started_at,completed_at,note) VALUES (?,?,?,'COMPLETED',?,?,NULL)", completed, tutor.id, pet, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        assertThat(post(tutor.token, "{\"events\":[" + event(UUID.randomUUID(), fixture.buttonId, fixture.espId, "button-a", 2, completed, "BEHAVIORAL") + "]}").body()).contains("TRAINING_SESSION_INVALID");
    }

    @Test
    void concurrentRetriesProduceOneFactAndOneAcceptance() throws Exception {
        Tutor tutor = register(); Fixture fixture = fixture(tutor); UUID id = UUID.randomUUID();
        String body = "{\"events\":[" + event(id, fixture.buttonId, fixture.espId, "button-a", 3, null, "BEHAVIORAL") + "]}";
        CompletableFuture<HttpResponse<String>> left = CompletableFuture.supplyAsync(() -> unchecked(tutor.token, body));
        CompletableFuture<HttpResponse<String>> right = CompletableFuture.supplyAsync(() -> unchecked(tutor.token, body));
        String both = left.get().body() + right.get().body();
        assertThat(both).contains("ACCEPTED", "ALREADY_ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM button_event WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void exposesHistoryAndKeepsHumanMetadataOutsideTheRawFact() throws Exception {
        Tutor tutor = register(); Fixture fixture = fixture(tutor); UUID eventId = UUID.randomUUID();
        assertThat(post(tutor.token, "{\"events\":[" + event(eventId, fixture.buttonId, fixture.espId, "button-a", 8, null, "TEST") + "]}").body()).contains("ACCEPTED");
        HttpResponse<String> history = get(tutor.token, "/button-events?purpose=TEST&limit=1");
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(history.body()).contains(eventId.toString(), "buttonNameSnapshot").doesNotContain("playback");
        HttpResponse<String> created = postPath(tutor.token, "/context-types", "{\"name\":\"Near dinner\"}", UUID.randomUUID().toString());
        assertThat(created.statusCode()).isEqualTo(201);
        UUID contextType = UUID.fromString(json.readTree(created.body()).at("/data/contextType/id").asText());
        HttpResponse<String> context = put(tutor.token, "/button-events/" + eventId + "/context", "{\"contextTypeId\":\"" + contextType + "\",\"note\":\"Asked for food\"}", null);
        assertThat(context.statusCode()).as(context.body()).isEqualTo(200);
        assertThat(put(tutor.token, "/button-events/" + eventId + "/classification", "{\"value\":\"UNCERTAIN\"}", null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM button_event WHERE id=?", Integer.class, eventId)).isEqualTo(1);
        assertThat(get(tutor.token, "/button-events/" + eventId).body()).contains("Near dinner", "Asked for food", "UNCERTAIN");
    }

    private Fixture fixture(Tutor tutor) throws Exception {
        UUID button = UUID.fromString(json.readTree(postPath(tutor.token, "/buttons", "{\"name\":\"Food\"}", UUID.randomUUID().toString()).body()).get("data").get("button").get("id").asText());
        UUID esp = UUID.randomUUID(); UUID binding = UUID.randomUUID(); Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO esp32_device(id,tutor_id,status,created_at) VALUES (?,?,'ACTIVE',?)", esp, tutor.id, now);
        jdbc.update("INSERT INTO physical_button_binding(id,esp32_device_id,physical_button_id,button_id,status,created_at) VALUES (?,?,'button-a',?,'ACTIVE',?)", binding, esp, button, now);
        return new Fixture(button, esp);
    }
    private UUID pet(String token) throws Exception { return UUID.fromString(json.readTree(postPath(token, "/pets", "{\"name\":\"Pet\",\"species\":\"DOG\"}", UUID.randomUUID().toString()).body()).get("data").get("pet").get("id").asText()); }
    private Tutor register() throws Exception { JsonNode data = json.readTree(postPath(null, "/auth/register", "{\"name\":\"Tutor\",\"email\":\"event-" + UUID.randomUUID() + "@example.com\",\"password\":\"long-password-123456\"}", UUID.randomUUID().toString()).body()).get("data"); return new Tutor(UUID.fromString(data.get("tutor").get("id").asText()), data.get("session").get("accessToken").asText()); }
    private String event(UUID id, UUID button, UUID esp, String physical, long sequence, UUID training, String purpose) { return "{\"id\":\"" + id + "\",\"buttonId\":\"" + button + "\",\"esp32DeviceId\":\"" + esp + "\",\"physicalButtonId\":\"" + physical + "\",\"espSessionId\":\"" + UUID.nameUUIDFromBytes(("session" + sequence).getBytes()) + "\",\"sequence\":" + sequence + ",\"espUptimeMs\":100,\"occurredAt\":\"2026-09-16T00:00:00Z\",\"receivedAt\":\"2026-09-16T00:00:01Z\",\"timeQuality\":\"ESTIMATED_FROM_MOBILE\",\"transport\":\"BLE\",\"purpose\":\"" + purpose + "\",\"trainingSessionId\":" + (training == null ? "null" : "\"" + training + "\"") + "}"; }
    private HttpResponse<String> post(String token, String body) throws Exception { return postPath(token, "/sync/button-events", body, null); }
    private HttpResponse<String> get(String token, String path) throws Exception { return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token).header("X-Request-Id", "event-test").GET().build(), HttpResponse.BodyHandlers.ofString()); }
    private HttpResponse<String> put(String token, String path, String body, String version) throws Exception { HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token).header("Content-Type", "application/json").header("X-Request-Id", "event-test").PUT(HttpRequest.BodyPublishers.ofString(body)); if(version!=null) builder.header("If-Match",version); return client.send(builder.build(),HttpResponse.BodyHandlers.ofString()); }
    private HttpResponse<String> unchecked(String token, String body) { try { return post(token, body); } catch (Exception e) { throw new AssertionError(e); } }
    private HttpResponse<String> postPath(String token, String path, String body, String key) throws Exception { HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json").header("X-Request-Id", "event-test").POST(HttpRequest.BodyPublishers.ofString(body)); if (token != null) b.header("Authorization", "Bearer " + token); if (key != null) b.header("Idempotency-Key", key); return client.send(b.build(), HttpResponse.BodyHandlers.ofString()); }
    private record Tutor(UUID id, String token) {} private record Fixture(UUID buttonId, UUID espId) {}
}
