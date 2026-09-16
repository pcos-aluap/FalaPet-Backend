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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "falapet.auth.rate.max-attempts=100")
@ExtendWith(OutputCaptureExtension.class)
class ButtonIntegrationTest extends PostgreSqlIntegrationTest {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort private int port;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsIdempotentlyWithOnlyLogicalFieldsAndOwnership(CapturedOutput logs) throws Exception {
        String owner = register();
        String other = register();
        String key = "button-key-" + UUID.randomUUID();
        HttpResponse<String> created = send("POST", "/buttons", "{\"name\":\" Comida \",\"description\":null}", owner, key, null);
        JsonNode button = button(created);
        assertThat(created.headers().firstValue("X-Request-Id")).contains("button-test");
        assertThat(keys(button)).isEqualTo(keys(fixture("button-detail-v1.json").get("data").get("button")));
        assertThat(button.get("name").asText()).isEqualTo("Comida");
        assertThat(button.get("activeAudio").isNull()).isTrue();
        assertThat(button.get("activeBinding").isNull()).isTrue();
        String id = button.get("id").asText();
        assertThat(button(send("POST", "/buttons", "{\"description\":null,\"name\":\" Comida \"}", owner, key, null)).get("id").asText()).isEqualTo(id);
        assertThat(send("POST", "/buttons", "{\"name\":\"Outro\"}", owner, key, null).body()).contains("DUPLICATE_RESOURCE");
        assertThat(send("GET", "/buttons/" + id, null, other, null, null).statusCode()).isEqualTo(404);
        assertThat(send("PATCH", "/buttons/" + id, "{\"name\":\"Roubo\"}", other, null, "\"1\"").statusCode()).isEqualTo(404);
        assertThat(send("GET", "/buttons", null, other, null, null).body()).contains("\"items\":[]");
        for (String invalid : new String[]{"{}", "{\"name\":\"\"}", "{\"name\":\"A\",\"petId\":\"x\"}", "{\"name\":\"A\",\"audioUrl\":\"x\"}"}) {
            assertThat(send("POST", "/buttons", invalid, owner, UUID.randomUUID().toString(), null).body()).contains("VALIDATION_ERROR");
        }
        assertThat(logs.getOut()).doesNotContain(owner, other, key);
        assertThat(logs.getErr()).doesNotContain(owner, other, key);
    }

    @Test
    void editsWithIfMatchAndSerializesConcurrentUpdates() throws Exception {
        String access = register();
        JsonNode initial = button(create(access, "{\"name\":\"Comida\",\"description\":\"Pedido\"}"));
        String path = "/buttons/" + initial.get("id").asText();
        assertThat(send("PATCH", path, "{\"name\":\"Água\"}", access, null, null).body()).contains("VALIDATION_ERROR");
        JsonNode changed = button(send("PATCH", path, "{\"description\":null}", access, null, "\"1\""));
        assertThat(changed.get("name").asText()).isEqualTo("Comida");
        assertThat(changed.get("description").isNull()).isTrue();
        assertThat(send("PATCH", path, "{\"name\":\"Água\"}", access, null, "\"1\"").body()).contains("VERSION_CONFLICT");
        CompletableFuture<HttpResponse<String>> left = CompletableFuture.supplyAsync(() -> uncheckedSend("PATCH", path, "{\"name\":\"A\"}", access, null, "\"2\""));
        CompletableFuture<HttpResponse<String>> right = CompletableFuture.supplyAsync(() -> uncheckedSend("PATCH", path, "{\"name\":\"B\"}", access, null, "\"2\""));
        HttpResponse<String> one = left.get();
        HttpResponse<String> two = right.get();
        assertThat(one.statusCode() == 200 ^ two.statusCode() == 200).isTrue();
        assertThat((one.statusCode() == 409 ? one : two).body()).contains("VERSION_CONFLICT");
    }

    @Test
    void listsWithCursorAndLogicallyDeactivatesWithoutBindingOrAudio() throws Exception {
        String access = register();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 4; i++) ids.add(button(create(access, "{\"name\":\"Button " + i + "\"}")).get("id").asText());
        String id = ids.iterator().next();
        String path = "/buttons/" + id + "/deactivation";
        JsonNode inactive = button(send("POST", path, null, access, null, null));
        assertThat(inactive.get("status").asText()).isEqualTo("INACTIVE");
        assertThat(button(send("POST", path, null, access, null, null)).get("version").asLong()).isEqualTo(inactive.get("version").asLong());
        assertThat(json.readTree(send("GET", "/buttons?status=ACTIVE", null, access, null, null).body()).get("data").get("items")).hasSize(3);
        String cursor = json.readTree(send("GET", "/buttons?status=ALL&limit=1", null, access, null, null).body()).get("data").get("page").get("nextCursor").asText();
        assertThat(send("GET", "/buttons?status=ACTIVE&limit=1&cursor=" + cursor, null, access, null, null).body()).contains("INVALID_CURSOR");
        JsonNode active = button(send("DELETE", path, null, access, null, null));
        assertThat(active.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM button WHERE id = ?", Integer.class, UUID.fromString(id))).isEqualTo(1);
        assertThat(send("GET", "/buttons/" + id + "/binding", null, access, null, null).statusCode()).isEqualTo(404);
        assertThat(send("POST", "/buttons/" + id + "/audio-uploads", "{}", access, UUID.randomUUID().toString(), null).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name LIKE '%audio%' OR table_name LIKE '%binding%'", Integer.class)).isZero();
    }

    private String register() throws Exception {
        JsonNode data = json.readTree(send("POST", "/auth/register", "{\"name\":\"Tutor\",\"email\":\"button-" + UUID.randomUUID() + "@example.com\",\"password\":\"long-password-123456\"}", null, UUID.randomUUID().toString(), null).body()).get("data");
        return data.get("session").get("accessToken").asText();
    }
    private HttpResponse<String> create(String access, String body) throws Exception { return send("POST", "/buttons", body, access, UUID.randomUUID().toString(), null); }
    private JsonNode button(HttpResponse<String> response) throws Exception { assertThat(response.statusCode()).isIn(200, 201); return json.readTree(response.body()).get("data").get("button"); }
    private Set<String> keys(JsonNode node) { Set<String> keys = new HashSet<>(); node.properties().forEach(entry -> keys.add(entry.getKey())); return keys; }
    private JsonNode fixture(String name) throws Exception { try (var stream = getClass().getResourceAsStream("/contracts/" + name)) { assertThat(stream).isNotNull(); return json.readTree(stream.readAllBytes()); } }
    private HttpResponse<String> uncheckedSend(String method, String path, String body, String access, String key, String ifMatch) { try { return send(method, path, body, access, key, ifMatch); } catch (Exception exception) { throw new AssertionError(exception); } }
    private HttpResponse<String> send(String method, String path, String body, String access, String key, String ifMatch) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).timeout(Duration.ofSeconds(20)).header("X-Request-Id", "button-test");
        if (access != null) request.header("Authorization", "Bearer " + access);
        if (key != null) request.header("Idempotency-Key", key);
        if (ifMatch != null) request.header("If-Match", ifMatch);
        if ("GET".equals(method)) request.GET(); else if ("DELETE".equals(method)) request.DELETE(); else request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
