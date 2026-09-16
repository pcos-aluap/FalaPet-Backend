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
class MobileDeviceIntegrationTest extends PostgreSqlIntegrationTest {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort private int port;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void registersAndroidInstallationWithDisabledPreferenceAndIdempotentMetadataUpdate(
            CapturedOutput logs) throws Exception {
        String access = register();
        UUID deviceId = UUID.randomUUID();
        assertThat(send("PUT", deviceId.toString(), registration("1.0.0"), access, null).statusCode())
                .isEqualTo(204);
        HttpResponse<String> replay = send("PUT", deviceId.toString(), registration("1.0.1"), access, null);
        assertThat(replay.statusCode()).isEqualTo(204);
        assertThat(replay.headers().firstValue("X-Request-Id")).contains("device-test");
        JsonNode preference = preference(send("GET", deviceId + "/preferences", null, access, null));
        assertThat(preference).isEqualTo(fixture("mobile-device-preference-v1.json").get("data")
                .get("preference"));
        assertThat(jdbc.queryForObject("SELECT app_version FROM mobile_device WHERE id = ?", String.class,
                deviceId)).isEqualTo("1.0.1");
        preference(send("PATCH", deviceId + "/preferences", "{\"localPlaybackEnabled\":true}",
                access, "\"1\""));
        assertThat(send("PUT", deviceId.toString(), registration("1.0.2"), access, null).statusCode())
                .isEqualTo(204);
        assertThat(preference(send("GET", deviceId + "/preferences", null, access, null))
                .get("localPlaybackEnabled").asBoolean()).isTrue();
        assertThat(logs.getOut()).doesNotContain(access, "1.0.1");
        assertThat(logs.getErr()).doesNotContain(access, "1.0.1");
    }

    @Test
    void isolatesTutorsAndOnlyAllowsApprovedRegistrationFields() throws Exception {
        String owner = register();
        String other = register();
        UUID deviceId = UUID.randomUUID();
        assertThat(send("PUT", deviceId.toString(), registration("1"), owner, null).statusCode()).isEqualTo(204);
        assertThat(send("GET", deviceId + "/preferences", null, other, null).statusCode()).isEqualTo(404);
        assertThat(send("PATCH", deviceId + "/preferences", "{\"localPlaybackEnabled\":true}",
                other, "\"1\"").statusCode()).isEqualTo(404);
        assertThat(send("PUT", deviceId.toString(), registration("2"), other, null).statusCode()).isEqualTo(404);
        for (String body : new String[]{"{}", "{\"platform\":\"IOS\",\"appVersion\":\"1\",\"localPlaybackEnabled\":false}",
                "{\"platform\":\"ANDROID\",\"appVersion\":\"1\",\"localPlaybackEnabled\":true}",
                "{\"platform\":\"ANDROID\",\"appVersion\":\"1\",\"localPlaybackEnabled\":false,\"tutorId\":\"x\"}"}) {
            assertThat(send("PUT", UUID.randomUUID().toString(), body, owner, null).body())
                    .contains("VALIDATION_ERROR");
        }
        assertThat(send("GET", "not-a-uuid/preferences", null, owner, null).body())
                .contains("VALIDATION_ERROR");
        assertThat(send("GET", deviceId + "/preferences", null, null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void changesOnlyOneInstallationWithStrictIfMatchAndDetectsConcurrentUpdates() throws Exception {
        String access = register();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        register(access, first);
        register(access, second);
        String firstPath = first + "/preferences";
        assertThat(send("PATCH", firstPath, "{\"localPlaybackEnabled\":true}", access, null).body())
                .contains("VALIDATION_ERROR");
        JsonNode changed = preference(send("PATCH", firstPath, "{\"localPlaybackEnabled\":true}",
                access, "\"1\""));
        assertThat(changed.get("localPlaybackEnabled").asBoolean()).isTrue();
        assertThat(changed.get("version").asLong()).isEqualTo(2);
        assertThat(preference(send("GET", second + "/preferences", null, access, null))
                .get("localPlaybackEnabled").asBoolean()).isFalse();
        assertThat(send("PATCH", firstPath, "{\"localPlaybackEnabled\":false}", access, "\"1\"").body())
                .contains("VERSION_CONFLICT");
        CompletableFuture<HttpResponse<String>> one = CompletableFuture.supplyAsync(() ->
                uncheckedSend("PATCH", firstPath, "{\"localPlaybackEnabled\":false}", access, "\"2\""));
        CompletableFuture<HttpResponse<String>> two = CompletableFuture.supplyAsync(() ->
                uncheckedSend("PATCH", firstPath, "{\"localPlaybackEnabled\":true}", access, "\"2\""));
        HttpResponse<String> left = one.get();
        HttpResponse<String> right = two.get();
        assertThat(left.statusCode() == 200 ^ right.statusCode() == 200).isTrue();
        HttpResponse<String> conflict = left.statusCode() == 409 ? left : right;
        assertThat(conflict.body()).contains("VERSION_CONFLICT");
        assertThat(json.readTree(conflict.body()).get("error").get("details").get("preference")
                .get("version").asLong()).isEqualTo(3);
    }

    @Test
    void persistsAndReturnsOnlyTheApprovedPreferenceNotPlaybackTelemetry() throws Exception {
        String access = register();
        UUID device = UUID.randomUUID();
        register(access, device);
        JsonNode returned = preference(send("GET", device + "/preferences", null, access, null));
        Set<String> fields = new HashSet<>();
        returned.properties().forEach(entry -> fields.add(entry.getKey()));
        assertThat(fields).containsExactlyInAnyOrder("localPlaybackEnabled", "version");
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = 'mobile_device'
                """, String.class)).containsExactlyInAnyOrder("id", "tutor_id", "platform", "app_version",
                "local_playback_enabled", "preference_version", "created_at", "updated_at");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name LIKE '%audio%'",
                Integer.class)).isZero();
        assertThat(send("GET", "/esp32-devices", null, access, null).statusCode()).isEqualTo(404);
    }

    private void register(String access, UUID deviceId) throws Exception {
        assertThat(send("PUT", deviceId.toString(), registration("1.0.0"), access, null).statusCode()).isEqualTo(204);
    }

    private String registration(String version) {
        return "{\"platform\":\"ANDROID\",\"appVersion\":\"" + version
                + "\",\"localPlaybackEnabled\":false}";
    }

    private String register() throws Exception {
        String email = "device-" + UUID.randomUUID() + "@example.com";
        JsonNode data = json.readTree(send("POST", "/auth/register", "{\"name\":\"Tutor\",\"email\":\""
                + email + "\",\"password\":\"long-password-123456\"}", null, null).body()).get("data");
        return data.get("session").get("accessToken").asText();
    }

    private JsonNode preference(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("data").get("preference");
    }

    private JsonNode fixture(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/contracts/" + name)) {
            assertThat(stream).isNotNull();
            return json.readTree(stream.readAllBytes());
        }
    }

    private HttpResponse<String> uncheckedSend(String method, String path, String body, String access,
            String ifMatch) {
        try { return send(method, path, body, access, ifMatch); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private HttpResponse<String> send(String method, String path, String body, String access,
            String ifMatch) throws Exception {
        String route = path.startsWith("/") ? path : "/mobile-devices/" + path;
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + route))
                .timeout(Duration.ofSeconds(20)).header("X-Request-Id", "device-test");
        if (access != null) request.header("Authorization", "Bearer " + access);
        if (ifMatch != null) request.header("If-Match", ifMatch);
        if ("POST".equals(method)) request.header("Idempotency-Key", "device-test-" + UUID.randomUUID());
        if ("GET".equals(method)) request.GET();
        else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
