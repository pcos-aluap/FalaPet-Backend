package com.falapet;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.falapet.auth.application.AuthStore;
import com.falapet.auth.infrastructure.CryptographicTokens;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "falapet.auth.rate.max-attempts=100")
@ExtendWith(OutputCaptureExtension.class)
class AuthHttpIntegrationTest extends PostgreSqlIntegrationTest {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort private int port;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthStore store;
    @Autowired private CryptographicTokens tokens;

    @Test
    void registrationLoginBearerLogoutAndUnavailableCapabilitiesFollowContract() throws Exception {
        String email = "Tutor-" + UUID.randomUUID() + "@example.com";
        String password = "correct-long-password-123";
        String key = UUID.randomUUID().toString();
        String body = "{\"name\":\"Paula\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> created = post("/auth/register", body, key, null);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("X-Request-Id")).contains("auth-test");
        assertThat(created.headers().firstValue("Cache-Control")).contains("no-store");
        JsonNode account = data(created);
        String access = account.get("session").get("accessToken").asText();
        String refresh = account.get("session").get("refreshToken").asText();
        assertThat(access).matches("[A-Za-z0-9_-]{43}");
        assertThat(refresh).matches("[A-Za-z0-9_-]{43}");
        assertThat(account.get("tutor").get("email").asText()).isEqualTo(email.toLowerCase());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_user WHERE email_normalized = ?",
                Integer.class, email.toLowerCase())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_refresh_token WHERE token_hash = ?",
                Integer.class, refresh)).isZero();

        HttpResponse<String> replay = post("/auth/register", body, key, null);
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(created.body());
        HttpResponse<String> duplicate = post("/auth/register", body.replace(email, email.toUpperCase()),
                UUID.randomUUID().toString(), null);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("EMAIL_ALREADY_REGISTERED");
        assertThat(post("/auth/register", body.replace(password, "short"), UUID.randomUUID().toString(), null)
                .body()).contains("VALIDATION_ERROR");

        HttpResponse<String> missing = get("/auth/session", null);
        assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(missing.body()).contains("SESSION_INVALID");
        assertThat(missing.headers().firstValue("Cache-Control")).contains("no-store");
        HttpResponse<String> session = get("/auth/session", access);
        assertThat(session.statusCode()).isEqualTo(200);
        assertThat(data(session).get("sessionId").asText())
                .isEqualTo(account.get("session").get("sessionId").asText());
        assertThat(data(session).get("tutor").get("name").asText()).isEqualTo("Paula");
        assertThat(get("/auth/session", "bad-token").body()).contains("SESSION_INVALID");
        jdbc.update("""
                UPDATE auth_user_session SET access_expires_at = created_at + interval '1 millisecond'
                WHERE access_token_hash = ?
                """, tokens.hash(access));
        assertThat(get("/auth/session", access).body()).contains("TOKEN_EXPIRED");

        String invalid = "{\"email\":\"none@example.com\",\"password\":\"" + password + "\"}";
        assertThat(post("/auth/login", invalid, null, null).body()).contains("INVALID_CREDENTIALS");
        assertThat(post("/auth/login", invalid.replace("none@example.com", email)
                .replace(password, "wrong-long-password"), null, null).body()).contains("INVALID_CREDENTIALS");
        HttpResponse<String> login = post("/auth/login",
                "{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"" + password + "\"}",
                null, null);
        assertThat(login.statusCode()).isEqualTo(200);
        String secondAccess = data(login).get("session").get("accessToken").asText();

        String logoutKey = UUID.randomUUID().toString();
        assertThat(post("/auth/logout", "{}", logoutKey, secondAccess).statusCode()).isEqualTo(204);
        assertThat(post("/auth/logout", "{}", logoutKey, secondAccess).statusCode()).isEqualTo(204);
        assertThat(get("/auth/session", secondAccess).body()).contains("SESSION_INVALID");
        assertThat(get("/auth/session", access).body()).contains("TOKEN_EXPIRED");

        assertThat(post("/auth/password-recovery/request", "{\"email\":\"" + email + "\"}",
                UUID.randomUUID().toString(), null).statusCode()).isEqualTo(202);
        assertThat(post("/auth/password-recovery/request", "{\"email\":\"absent@example.com\"}",
                UUID.randomUUID().toString(), null).body()).isEqualTo("{\"data\":{\"accepted\":true}}");
        HttpResponse<String> google = post("/auth/google", "{\"idToken\":\"opaque\"}", null, null);
        assertThat(google.statusCode()).isEqualTo(403);
        assertThat(google.body()).contains("GOOGLE_LOGIN_UNAVAILABLE");
        assertThat(get("/auth/capabilities", null).body())
                .isEqualTo("{\"data\":{\"passwordLogin\":true,\"googleLogin\":false,\"passwordRecovery\":false}}");
    }

    @Test
    void concurrentRegistrationUsesUniqueEmailAndLogsExcludeSecrets(CapturedOutput output) throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.com";
        String password = "sensitive-long-password-123";
        String body = "{\"name\":\"Tutor\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}";
        CompletableFuture<HttpResponse<String>> first = CompletableFuture.supplyAsync(() ->
                uncheckedRegister(body, UUID.randomUUID().toString()));
        CompletableFuture<HttpResponse<String>> second = CompletableFuture.supplyAsync(() ->
                uncheckedRegister(body.replace(email, email.toUpperCase()), UUID.randomUUID().toString()));
        HttpResponse<String> a = first.get();
        HttpResponse<String> b = second.get();
        assertThat(a.statusCode() == 201 ^ b.statusCode() == 201).isTrue();
        assertThat(a.statusCode() == 409 || b.statusCode() == 409).isTrue();
        String access = data(a.statusCode() == 201 ? a : b).get("session").get("accessToken").asText();
        assertThat(get("/auth/session", access).statusCode()).isEqualTo(200);
        assertThat(output.getOut()).doesNotContain(password, access, email);
        assertThat(output.getErr()).doesNotContain(password, access, email);
    }

    @Test
    void refreshIsSingleUseUnderConcurrencyAndReuseRevokesFamily() throws Exception {
        String email = "refresh-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> created = post("/auth/register",
                "{\"name\":\"Tutor\",\"email\":\"" + email
                        + "\",\"password\":\"long-password-123456\"}", UUID.randomUUID().toString(), null);
        String first = data(created).get("session").get("refreshToken").asText();
        String body = "{\"refreshToken\":\"" + first + "\"}";
        CompletableFuture<HttpResponse<String>> one = CompletableFuture.supplyAsync(() -> uncheckedPost(body));
        CompletableFuture<HttpResponse<String>> two = CompletableFuture.supplyAsync(() -> uncheckedPost(body));
        HttpResponse<String> a = one.get();
        HttpResponse<String> b = two.get();
        assertThat(a.statusCode() == 200 ^ b.statusCode() == 200).isTrue();
        assertThat(a.statusCode() == 401 || b.statusCode() == 401).isTrue();
        String next = data(a.statusCode() == 200 ? a : b).get("refreshToken").asText();
        assertThat(post("/auth/refresh", "{\"refreshToken\":\"" + next + "\"}", null, null)
                .body()).contains("SESSION_INVALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_refresh_family WHERE revoked_at IS NOT NULL",
                Integer.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void recoveryConfirmationConsumesTokenAndRevokesExistingSessions() throws Exception {
        String email = "recovery-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> created = post("/auth/register",
                "{\"name\":\"Tutor\",\"email\":\"" + email
                        + "\",\"password\":\"long-password-123456\"}", UUID.randomUUID().toString(), null);
        JsonNode account = data(created);
        String access = account.get("session").get("accessToken").asText();
        String refresh = account.get("session").get("refreshToken").asText();
        UUID userId = UUID.fromString(account.get("tutor").get("id").asText());
        String recovery = tokens.generate();
        store.createRecovery(userId, UUID.randomUUID(), tokens.hash(recovery), Instant.now(),
                Instant.now().plus(Duration.ofMinutes(20)));
        String confirm = "{\"recoveryToken\":\"" + recovery
                + "\",\"newPassword\":\"new-long-password-654321\"}";
        CompletableFuture<HttpResponse<String>> one = CompletableFuture.supplyAsync(() -> uncheckedConfirm(confirm));
        CompletableFuture<HttpResponse<String>> two = CompletableFuture.supplyAsync(() -> uncheckedConfirm(confirm));
        HttpResponse<String> a = one.get();
        HttpResponse<String> b = two.get();
        assertThat(a.statusCode() == 204 ^ b.statusCode() == 204).isTrue();
        assertThat(a.statusCode() == 400 || b.statusCode() == 400).isTrue();
        assertThat(post("/auth/password-recovery/confirm", confirm, null, null).body())
                .contains("RECOVERY_TOKEN_INVALID");
        assertThat(get("/auth/session", access).body()).contains("SESSION_INVALID");
        assertThat(post("/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}", null, null)
                .body()).contains("SESSION_INVALID");
        assertThat(post("/auth/login", "{\"email\":\"" + email
                + "\",\"password\":\"new-long-password-654321\"}", null, null).statusCode()).isEqualTo(200);

        String expired = tokens.generate();
        store.createRecovery(userId, UUID.randomUUID(), tokens.hash(expired), Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60));
        assertThat(post("/auth/password-recovery/confirm", confirm.replace(recovery, expired), null, null)
                .body()).contains("RECOVERY_TOKEN_INVALID");
    }

    private HttpResponse<String> uncheckedPost(String body) {
        try {
            return post("/auth/refresh", body, null, null);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private HttpResponse<String> uncheckedRegister(String body, String key) {
        try {
            return post("/auth/register", body, key, null);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private HttpResponse<String> uncheckedConfirm(String body) {
        try {
            return post("/auth/password-recovery/confirm", body, null, null);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private JsonNode data(HttpResponse<String> response) {
        return json.readTree(response.body()).get("data");
    }

    private HttpResponse<String> get(String path, String access) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20))
                .header("X-Request-Id", "auth-test").GET();
        if (access != null) builder.header("Authorization", "Bearer " + access);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String key, String access) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20))
                .header("X-Request-Id", "auth-test").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (key != null) builder.header("Idempotency-Key", key);
        if (access != null) builder.header("Authorization", "Bearer " + access);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + "/api/v1" + path);
    }
}
