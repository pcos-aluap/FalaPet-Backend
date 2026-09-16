package com.falapet;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "falapet.auth.rate.max-attempts=100")
@ExtendWith(OutputCaptureExtension.class)
class PetIntegrationTest extends PostgreSqlIntegrationTest {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort private int port;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsValidPetAndIsolatesOwnershipAndSchema(CapturedOutput logs) throws Exception {
        String a = register();
        String b = register();
        String key = "pet-private-key-" + UUID.randomUUID();
        String body = "{\"name\":\" Marley \",\"species\":\"DOG\",\"birthDate\":\"2022-05-10\",\"sex\":\"MALE\"}";
        HttpResponse<String> created = send("POST", "/pets", body, a, key, null);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("X-Request-Id")).contains("pet-test");
        JsonNode pet = pet(created);
        assertThat(keys(pet)).isEqualTo(keys(fixture("pet-detail-null-photo-v1.json")
                .get("data").get("pet")));
        assertThat(pet.get("name").asText()).isEqualTo("Marley");
        assertThat(pet.get("photo").isNull()).isTrue();
        assertThat(pet.get("version").asLong()).isEqualTo(1);
        String id = pet.get("id").asText();
        assertThat(json.readTree(send("GET", "/pets", null, b, null, null).body())
                .get("data").get("items")).isEmpty();
        assertThat(pet(send("POST", "/pets", body, a, key, null)).get("id").asText()).isEqualTo(id);
        assertThat(send("POST", "/pets", "{\"name\":\"Other\",\"species\":\"CAT\"}",
                a, key, null).body()).contains("DUPLICATE_RESOURCE");
        assertThat(pet(send("GET", "/pets/" + id, null, a, null, null)).get("id").asText())
                .isEqualTo(id);
        for (String method : new String[]{"GET", "PATCH", "POST", "DELETE"}) {
            String path = "/pets/" + id + (method.equals("POST") || method.equals("DELETE")
                    ? "/deactivation" : "");
            HttpResponse<String> response = send(method, path,
                    method.equals("PATCH") ? "{\"name\":\"Stolen\"}" : null,
                    b, null, method.equals("PATCH") ? "\"1\"" : null);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).contains("RESOURCE_NOT_FOUND");
        }
        assertThat(send("GET", "/pets/" + UUID.randomUUID(), null, a, null, null).statusCode())
                .isEqualTo(404);
        assertThat(send("GET", "/pets/" + id, null, null, null, null).statusCode())
                .isEqualTo(401);
        assertThat(send("PATCH", "/pets/" + id, "{\"name\":\"Changed\"}",
                a, null, "\"1\"").statusCode()).isEqualTo(200);
        assertThat(send("POST", "/pets", body, a, key, null).body()).isEqualTo(created.body());
        assertThat(logs.getOut()).doesNotContain(a, b, key, body);
        assertThat(logs.getErr()).doesNotContain(a, b, key, body);
    }

    @Test
    void validatesCreationAndPartialPatchAndSerializesConcurrentIfMatch() throws Exception {
        String access = register();
        for (String bad : new String[]{"{}", "{\"name\":\"\",\"species\":\"DOG\"}",
                "{\"name\":\"A\",\"species\":\"BIRD\"}",
                "{\"name\":\"A\",\"species\":\"DOG\",\"sex\":\"OTHER\"}",
                "{\"name\":\"A\",\"species\":\"DOG\",\"birthDate\":\"2022-02-30\"}",
                "{\"name\":\"A\",\"species\":\"DOG\",\"tutorId\":\"" + UUID.randomUUID() + "\"}"}) {
            assertThat(send("POST", "/pets", bad, access, UUID.randomUUID().toString(), null)
                    .body()).contains("VALIDATION_ERROR");
        }
        assertThat(send("POST", "/pets", "{\"name\":\"A\",\"species\":\"DOG\"}",
                access, null, null).body()).contains("VALIDATION_ERROR");
        JsonNode initial = pet(create(access, "{\"name\":\"A\",\"species\":\"CAT\","
                + "\"birthDate\":\"2022-01-01\",\"sex\":\"UNKNOWN\"}"));
        String path = "/pets/" + initial.get("id").asText();
        assertThat(send("PATCH", path, "{\"name\":\"B\"}", access, null, null).body())
                .contains("VALIDATION_ERROR");
        assertThat(send("PATCH", path, "{\"photo\":null}", access, null, "\"1\"").body())
                .contains("VALIDATION_ERROR");
        JsonNode partial = pet(send("PATCH", path, "{\"name\":\"B\"}", access, null, "\"1\""));
        assertThat(partial.get("species").asText()).isEqualTo("CAT");
        assertThat(partial.get("sex").asText()).isEqualTo("UNKNOWN");
        assertThat(partial.get("birthDate").asText()).isEqualTo("2022-01-01");
        assertThat(partial.get("version").asLong()).isEqualTo(2);
        JsonNode cleared = pet(send("PATCH", path, "{\"birthDate\":null,\"sex\":null}",
                access, null, "\"2\""));
        assertThat(cleared.get("birthDate").isNull()).isTrue();
        assertThat(cleared.get("sex").isNull()).isTrue();
        assertThat(cleared.get("version").asLong()).isEqualTo(3);
        assertThat(send("PATCH", path, "{\"name\":\"C\"}", access, null, "3").body())
                .contains("VALIDATION_ERROR");
        CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(() ->
                uncheckedSend("PATCH", path, "{\"name\":\"First\"}", access, null, "\"3\""));
        CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(() ->
                uncheckedSend("PATCH", path, "{\"name\":\"Second\"}", access, null, "\"3\""));
        HttpResponse<String> one = first.get();
        HttpResponse<String> two = second.get();
        assertThat(one.statusCode() == 200 ^ two.statusCode() == 200).isTrue();
        assertThat((one.statusCode() == 409 ? one : two).body()).contains("VERSION_CONFLICT");
        assertThat(json.readTree((one.statusCode() == 409 ? one : two).body()).get("error")
                .get("details").get("pet").get("version").asLong()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT version FROM pet WHERE id = ?", Long.class,
                UUID.fromString(initial.get("id").asText()))).isEqualTo(4);
    }

    @Test
    void sameKeyConcurrentCreationProducesOnePetAndOneReplayMarker() throws Exception {
        String access = register();
        String key = "pet-create-" + UUID.randomUUID();
        String body = "{\"species\":\"OTHER\",\"name\":\"Concurrent\"}";
        CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(() ->
                uncheckedSend("POST", "/pets", body, access, key, null));
        CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(() ->
                uncheckedSend("POST", "/pets", "{\"name\":\"Concurrent\",\"species\":\"OTHER\"}",
                        access, key, null));
        HttpResponse<String> one = first.get();
        HttpResponse<String> two = second.get();
        assertThat(one.statusCode()).isEqualTo(201);
        assertThat(two.statusCode()).isEqualTo(201);
        assertThat(pet(one).get("id").asText()).isEqualTo(pet(two).get("id").asText());
        UUID id = UUID.fromString(pet(one).get("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pet WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pet_creation_idempotency WHERE pet_id = ?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void listsStatusWithOpaqueCursorWithoutGapsAndPreservesLogicalHistory() throws Exception {
        String access = register();
        Set<String> original = new HashSet<>();
        for (int i = 0; i < 6; i++) original.add(pet(create(access,
                "{\"name\":\"Pet " + i + "\",\"species\":\"DOG\"}"))
                .get("id").asText());
        String inactiveId = original.iterator().next();
        String path = "/pets/" + inactiveId + "/deactivation";
        JsonNode inactive = pet(send("POST", path, null, access, null, null));
        assertThat(inactive.get("status").asText()).isEqualTo("INACTIVE");
        assertThat(pet(send("POST", path, null, access, null, null)).get("version").asLong())
                .isEqualTo(inactive.get("version").asLong());
        Set<String> seen = new HashSet<>();
        String cursor = null;
        do {
            String pagePath = "/pets?status=ALL&limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode data = json.readTree(send("GET", pagePath, null, access, null, null).body()).get("data");
            for (JsonNode item : data.get("items")) assertThat(seen.add(item.get("id").asText())).isTrue();
            cursor = data.get("page").get("nextCursor").isNull()
                    ? null : data.get("page").get("nextCursor").asText();
        } while (cursor != null);
        assertThat(seen).isEqualTo(original);
        assertThat(json.readTree(send("GET", "/pets?status=INACTIVE", null, access, null, null)
                .body()).get("data").get("items")).hasSize(1);
        assertThat(json.readTree(send("GET", "/pets", null, access, null, null)
                .body()).get("data").get("items")).hasSize(5);
        assertThat(send("GET", "/pets?cursor=invalid", null, access, null, null).body())
                .contains("INVALID_CURSOR");
        String scopedCursor = json.readTree(send("GET", "/pets?status=ALL&limit=1", null,
                access, null, null).body()).get("data").get("page").get("nextCursor").asText();
        assertThat(send("GET", "/pets?status=ACTIVE&limit=1&cursor=" + scopedCursor,
                null, access, null, null).body()).contains("INVALID_CURSOR");
        assertThat(send("GET", "/pets?status=ALL&limit=1&cursor=" + scopedCursor,
                null, register(), null, null).body()).contains("INVALID_CURSOR");
        assertThat(send("GET", "/pets?limit=101", null, access, null, null).body())
                .contains("VALIDATION_ERROR");
        JsonNode active = pet(send("DELETE", path, null, access, null, null));
        assertThat(active.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(active.get("version").asLong()).isEqualTo(inactive.get("version").asLong() + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pet WHERE id = ?", Integer.class,
                UUID.fromString(inactiveId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name = 'button_event'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void capabilitiesDoNotEnableUnimplementedStorageOrAudio() throws Exception {
        HttpResponse<String> response = send("GET", "/media/capabilities", null, null, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = json.readTree(response.body()).get("data");
        assertThat(data).isEqualTo(fixture("media-capabilities-disabled-v1.json").get("data"));
        JsonNode photo = data.get("petPhoto");
        assertThat(photo.get("enabled").asBoolean()).isFalse();
        assertThat(photo.get("maxSizeBytes").asLong()).isEqualTo(5_242_880);
        assertThat(photo.get("minWidth").asInt()).isEqualTo(128);
        assertThat(photo.get("maxHeight").asInt()).isEqualTo(4096);
        assertThat(photo.get("sha256Required").asBoolean()).isTrue();
        assertThat(data.get("buttonAudio").get("enabled").asBoolean()).isFalse();
        assertThat(response.body()).doesNotContain("url", "headers", "instruction", "uploadId");

        String access = register();
        UUID petId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        assertThat(send("POST", "/pets/" + petId + "/photo-uploads", "{}", access,
                UUID.randomUUID().toString(), null).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/pets/" + petId + "/photo-uploads/" + uploadId + "/complete",
                "", access, UUID.randomUUID().toString(), null).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/pets/" + petId + "/photo", null, access,
                UUID.randomUUID().toString(), "\"1\"").statusCode()).isEqualTo(404);
        assertThat(send("POST", "/buttons/" + UUID.randomUUID() + "/audio-uploads", "{}", access,
                UUID.randomUUID().toString(), null).statusCode()).isEqualTo(404);
    }

    private String register() throws Exception {
        String email = "pet-" + UUID.randomUUID() + "@example.com";
        JsonNode data = json.readTree(send("POST", "/auth/register",
                "{\"name\":\"Tutor\",\"email\":\"" + email
                        + "\",\"password\":\"long-password-123456\"}",
                null, UUID.randomUUID().toString(), null).body()).get("data");
        return data.get("session").get("accessToken").asText();
    }

    private HttpResponse<String> create(String access, String body) throws Exception {
        return send("POST", "/pets", body, access, UUID.randomUUID().toString(), null);
    }

    private JsonNode pet(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isIn(200, 201);
        return json.readTree(response.body()).get("data").get("pet");
    }

    private Set<String> keys(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }

    private JsonNode fixture(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/contracts/" + name)) {
            assertThat(stream).isNotNull();
            return json.readTree(stream.readAllBytes());
        }
    }

    private HttpResponse<String> uncheckedSend(String method, String path, String body,
            String access, String key, String ifMatch) {
        try { return send(method, path, body, access, key, ifMatch); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private HttpResponse<String> send(String method, String path, String body, String access,
            String key, String ifMatch) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(20)).header("X-Request-Id", "pet-test");
        if (access != null) request.header("Authorization", "Bearer " + access);
        if (key != null) request.header("Idempotency-Key", key);
        if (ifMatch != null) request.header("If-Match", ifMatch);
        if ("GET".equals(method)) request.GET();
        else if ("DELETE".equals(method)) request.DELETE();
        else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
