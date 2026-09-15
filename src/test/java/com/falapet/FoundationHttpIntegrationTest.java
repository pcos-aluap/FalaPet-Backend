package com.falapet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoundationHttpIntegrationTest extends PostgreSqlIntegrationTest {

	private static final Pattern GENERATED_REQUEST_ID =
		Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	@LocalServerPort
	private int port;

	@Test
	void exposesOnlyHealthyActuatorEndpointAndPropagatesRequestId() throws Exception {
		HttpResponse<String> response = get("/actuator/health", "mobile-request_123");
		HttpResponse<String> hidden = get("/actuator/info", "hidden-1");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
		assertThat(response.headers().firstValue("X-Request-Id")).contains("mobile-request_123");
		assertThat(hidden.statusCode()).isEqualTo(403);
		assertThat(hidden.headers().firstValue("X-Request-Id")).contains("hidden-1");
		assertThat(hidden.body()).contains("\"code\":\"FORBIDDEN\"");
	}

	@Test
	void exposesOnlyContractedCapabilitiesAndGeneratesSafeRequestId() throws Exception {
		HttpResponse<String> capabilities = get("/api/v1/auth/capabilities", "capabilities-1");
		HttpResponse<String> missing = get("/api/v1/pets", null);
		HttpResponse<String> invalid = get("/actuator/health", "invalid request id with spaces");
		HttpResponse<String> excessive = get("/actuator/health", "x".repeat(129));

		assertThat(capabilities.statusCode()).isEqualTo(200);
		assertThat(capabilities.headers().firstValue("Content-Type"))
			.contains("application/json;charset=UTF-8");
		assertThat(capabilities.headers().firstValue("X-Request-Id")).contains("capabilities-1");
		assertThat(capabilities.body()).isEqualTo(
			"{\"data\":{\"passwordLogin\":true,\"googleLogin\":false,\"passwordRecovery\":false}}");

		assertThat(missing.statusCode()).isEqualTo(404);
		assertThat(missing.headers().firstValue("Content-Type"))
			.contains("application/json;charset=UTF-8");
		assertThat(missing.body()).contains("\"code\":\"RESOURCE_NOT_FOUND\"")
			.doesNotContain("trace", "exception", "SQLException");
		assertThat(missing.headers().firstValue("X-Request-Id")).hasValueSatisfying(
			value -> assertThat(value).matches(GENERATED_REQUEST_ID));
		assertThat(invalid.headers().firstValue("X-Request-Id")).hasValueSatisfying(
			value -> assertThat(value).matches(GENERATED_REQUEST_ID));
		assertThat(excessive.headers().firstValue("X-Request-Id")).hasValueSatisfying(
			value -> assertThat(value).matches(GENERATED_REQUEST_ID));
	}

	@Test
	void rejectsUnsupportedContentTypeWithContractErrorAndRequestId() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(
			URI.create("http://localhost:" + port + "/api/v1/not-implemented"))
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "text/plain")
			.header("X-Request-Id", "media-1")
			.POST(HttpRequest.BodyPublishers.ofString("not-json"))
			.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(415);
		assertThat(response.headers().firstValue("X-Request-Id")).contains("media-1");
		assertThat(response.body()).contains("\"code\":\"UNSUPPORTED_MEDIA_TYPE\"")
			.contains("\"retryable\":false")
			.doesNotContain("trace", "exception", "not-json");

		HttpRequest wrongCharset = HttpRequest.newBuilder(
			URI.create("http://localhost:" + port + "/api/v1/not-implemented"))
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json;charset=ISO-8859-1")
			.POST(HttpRequest.BodyPublishers.ofString("{}"))
			.build();
		assertThat(client.send(wrongCharset, HttpResponse.BodyHandlers.ofString()).statusCode())
			.isEqualTo(415);
	}

	@Test
	void distinguishesMethodNotAllowedAndPayloadTooLarge() throws Exception {
		HttpResponse<String> method = postJson("/api/v1/auth/capabilities", "{}", "method-1");
		HttpResponse<String> excessive = postJson(
			"/api/v1/not-implemented", "x".repeat(1_048_577), "payload-1");

		assertThat(method.statusCode()).isEqualTo(405);
		assertThat(method.body()).contains("\"code\":\"VALIDATION_ERROR\"");
		assertThat(method.headers().firstValue("X-Request-Id")).contains("method-1");
		assertThat(excessive.statusCode()).isEqualTo(413);
		assertThat(excessive.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
		assertThat(excessive.headers().firstValue("X-Request-Id")).contains("payload-1");
	}

	private HttpResponse<String> get(String path, String requestId)
			throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.timeout(Duration.ofSeconds(10))
			.GET();
		if (requestId != null) {
			request.header("X-Request-Id", requestId);
		}
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> postJson(String path, String body, String requestId)
			throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json")
			.header("X-Request-Id", requestId)
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
