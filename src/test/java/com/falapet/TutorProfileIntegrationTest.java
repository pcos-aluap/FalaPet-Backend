package com.falapet;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
class TutorProfileIntegrationTest extends PostgreSqlIntegrationTest {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort private int port;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void getReturnsOnlyAuthenticatedTutorsContractFieldsAndDoesNotLogSecrets(CapturedOutput output)
            throws Exception {
        String email = "profile-" + UUID.randomUUID() + "@example.com";
        String password = "long-secret-password-1234";
        HttpResponse<String> registration = send("POST", "/auth/register",
                "{\"name\":\"Paula\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}",
                null, UUID.randomUUID().toString());
        assertThat(registration.statusCode()).isEqualTo(201);
        JsonNode account = json.readTree(registration.body()).get("data");
        String access = account.get("session").get("accessToken").asText();
        String refresh = account.get("session").get("refreshToken").asText();
        assertThat(send("GET", "/users/me", null, null, null).statusCode()).isEqualTo(401);
        HttpResponse<String> profile = send("GET", "/users/me", null, access, null);
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(profile.headers().firstValue("X-Request-Id")).contains("profile-test");
        JsonNode tutor = json.readTree(profile.body()).get("data").get("tutor");
        assertThat(tutor.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrderElementsOf(keys(fixture("tutor-profile-success-v1.json")
                        .get("data").get("tutor")));
        assertThat(tutor.get("email").asText()).isEqualTo(email);
        assertThat(tutor.get("id").asText()).isEqualTo(account.get("tutor").get("id").asText());
        assertThat(profile.body()).doesNotContain(access, refresh, password, "passwordHash", "sessionId");
        assertThat(output.getOut()).doesNotContain(access, refresh, password, email);
        assertThat(output.getErr()).doesNotContain(access, refresh, password, email);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_user WHERE id = ?", Integer.class,
                UUID.fromString(tutor.get("id").asText()))).isEqualTo(1);
    }

    @Test
    void patchRequiresQuotedVersionRejectsEmailAndAllowsOnlyOneConcurrentUpdate() throws Exception {
        JsonNode account = register("update");
        String access = account.get("session").get("accessToken").asText();
        UUID tutorId = UUID.fromString(account.get("tutor").get("id").asText());
        assertThat(patch(access, "{\"name\":\"New\"}", null).statusCode()).isEqualTo(400);
        assertThat(patch(access, "{\"name\":\"New\"}", "1").body()).contains("VALIDATION_ERROR");
        assertThat(patch(access, "{\"name\":\"New\"}", "\"bad\"").statusCode()).isEqualTo(400);
        assertThat(patch(access, "{\"email\":\"other@example.com\"}", "\"1\"")
                .body()).contains("VALIDATION_ERROR");
        assertThat(patch(access, "{\"name\":\"New\",\"email\":\"other@example.com\"}", "\"1\"")
                .statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT version FROM auth_user WHERE id = ?", Long.class, tutorId))
                .isEqualTo(1L);
        CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(() ->
                uncheckedPatch(access, "{\"name\":\"First\"}", "\"1\""));
        CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(() ->
                uncheckedPatch(access, "{\"name\":\"Second\"}", "\"1\""));
        HttpResponse<String> a = first.get();
        HttpResponse<String> b = second.get();
        assertThat(a.statusCode() == 200 ^ b.statusCode() == 200).isTrue();
        assertThat(a.statusCode() == 409 || b.statusCode() == 409).isTrue();
        assertThat((a.statusCode() == 409 ? a : b).body()).contains("VERSION_CONFLICT");
        assertThat(json.readTree((a.statusCode() == 409 ? a : b).body())
                .get("error").get("details").get("tutor").get("version").asLong()).isEqualTo(2);
        assertThat(keys(json.readTree((a.statusCode() == 409 ? a : b).body()).get("error")))
                .containsExactlyInAnyOrderElementsOf(keys(fixture("tutor-version-conflict-v1.json")
                        .get("error")));
        JsonNode updated = json.readTree((a.statusCode() == 200 ? a : b).body()).get("data").get("tutor");
        assertThat(updated.get("version").asLong()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT version FROM auth_user WHERE id = ?", Long.class, tutorId))
                .isEqualTo(2L);
        assertThat(json.readTree(send("GET", "/users/me", null, access, null).body())
                .get("data").get("tutor").get("name").asText()).isEqualTo(updated.get("name").asText());
        assertThat(patch(access, "{\"name\":\"Stale\"}", "\"1\"").body())
                .contains("VERSION_CONFLICT");
        assertThat(patch(access, "{\"name\":\"  Final  \"}", "\"2\"").statusCode())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT name FROM auth_user WHERE id = ?", String.class, tutorId))
                .isEqualTo("Final");
    }

    @Test
    void deletionRequestIsPendingIdempotentConcurrentAndPreservesSession() throws Exception {
        JsonNode account = register("deletion");
        String access = account.get("session").get("accessToken").asText();
        UUID tutorId = UUID.fromString(account.get("tutor").get("id").asText());
        String key = UUID.randomUUID().toString();
        assertThat(send("POST", "/users/me/deletion-requests", "{}", access, null).statusCode())
                .isEqualTo(400);
        assertThat(uncheckedDeletion(access, UUID.randomUUID().toString(), "[]").body())
                .contains("VALIDATION_ERROR");
        CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(() ->
                uncheckedDeletion(access, key, "{}"));
        CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(() ->
                uncheckedDeletion(access, key, "{}"));
        HttpResponse<String> a = first.get();
        HttpResponse<String> b = second.get();
        assertThat(a.statusCode()).isEqualTo(202);
        assertThat(b.statusCode()).isEqualTo(202);
        assertThat(a.body()).isEqualTo(b.body());
        JsonNode data = json.readTree(a.body()).get("data");
        assertThat(keys(data)).containsExactlyInAnyOrderElementsOf(
                keys(fixture("tutor-deletion-request-success-v1.json").get("data")));
        assertThat(data.get("status").asText()).isEqualTo("PENDING");
        assertThat(data.get("effectiveAt").isNull()).isTrue();
        assertThat(UUID.fromString(data.get("requestId").asText())).isNotNull();
        assertThat(java.time.Instant.parse(data.get("requestedAt").asText())).isNotNull();
        assertThat(uncheckedDeletion(access, key, "{\"extra\":true}").body())
                .contains("DUPLICATE_RESOURCE");
        assertThat(uncheckedDeletion(access, UUID.randomUUID().toString(), "{}").body())
                .isEqualTo(a.body());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_deletion_request WHERE user_id = ?",
                Integer.class, tutorId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM user_deletion_request WHERE user_id = ?",
                String.class, tutorId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM auth_user_session WHERE user_id = ?",
                Boolean.class, tutorId)).isTrue();
        assertThat(send("GET", "/users/me", null, access, null).statusCode()).isEqualTo(200);
    }

    @Test
    void eachTutorOwnsOnlyItsProfileAndDeletionRequestAndSecretsStayOutOfLogs(CapturedOutput output)
            throws Exception {
        JsonNode first = register("owner-a");
        JsonNode second = register("owner-b");
        String firstAccess = first.get("session").get("accessToken").asText();
        String secondAccess = second.get("session").get("accessToken").asText();
        UUID firstId = UUID.fromString(first.get("tutor").get("id").asText());
        UUID secondId = UUID.fromString(second.get("tutor").get("id").asText());
        String key = "private-key-" + UUID.randomUUID();
        HttpResponse<String> firstProfile = send("GET", "/users/me", null, firstAccess, null);
        HttpResponse<String> secondProfile = send("GET", "/users/me", null, secondAccess, null);
        assertThat(firstProfile.body()).contains(firstId.toString()).doesNotContain(secondId.toString());
        assertThat(secondProfile.body()).contains(secondId.toString()).doesNotContain(firstId.toString());
        assertThat(patch(firstAccess, "{\"name\":\"Owner A\"}", "\"1\"").statusCode())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT name FROM auth_user WHERE id = ?", String.class, secondId))
                .isEqualTo("Tutor");
        HttpResponse<String> deletion = send("POST", "/users/me/deletion-requests", "{}", firstAccess, key);
        assertThat(deletion.statusCode()).isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_deletion_request WHERE user_id = ?",
                Integer.class, secondId)).isZero();
        assertThat(send("POST", "/users/me/deletion-requests", "{}", secondAccess, key).statusCode())
                .isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_deletion_request", Integer.class))
                .isGreaterThanOrEqualTo(2);
        assertThat(send("GET", "/users/me", null, firstAccess, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/users/me", null, secondAccess, null).statusCode()).isEqualTo(200);
        assertThat(output.getOut()).doesNotContain(firstAccess, secondAccess, key,
                first.get("session").get("refreshToken").asText());
        assertThat(output.getErr()).doesNotContain(firstAccess, secondAccess, key);
    }

    private JsonNode register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> response = send("POST", "/auth/register",
                "{\"name\":\"Tutor\",\"email\":\"" + email
                        + "\",\"password\":\"long-password-123456\"}",
                null, UUID.randomUUID().toString());
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("data");
    }

    private JsonNode fixture(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/contracts/" + name)) {
            assertThat(stream).isNotNull();
            return json.readTree(stream.readAllBytes());
        }
    }

    private java.util.List<String> keys(JsonNode node) {
        return node.properties().stream().map(java.util.Map.Entry::getKey).toList();
    }

    private HttpResponse<String> patch(String access, String body, String version) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/users/me"))
                .timeout(Duration.ofSeconds(20)).header("X-Request-Id", "profile-test")
                .header("Authorization", "Bearer " + access).header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        if (version != null) request.header("If-Match", version);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> uncheckedPatch(String access, String body, String version) {
        try { return patch(access, body, version); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private HttpResponse<String> uncheckedDeletion(String access, String key, String body) {
        try { return send("POST", "/users/me/deletion-requests", body, access, key); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private HttpResponse<String> send(String method, String path, String body, String access, String key)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(20)).header("X-Request-Id", "profile-test");
        if (access != null) request.header("Authorization", "Bearer " + access);
        if (key != null) request.header("Idempotency-Key", key);
        if ("GET".equals(method)) request.GET();
        else request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
