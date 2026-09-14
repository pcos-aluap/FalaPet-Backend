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

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
		assertThat(response.headers().firstValue("X-Request-Id")).contains("mobile-request_123");
		assertThat(get("/actuator/info", null).statusCode()).isEqualTo(403);
	}

	@Test
	void protectsFutureApiAndGeneratesSafeRequestId() throws Exception {
		HttpResponse<String> missing = get("/api/v1/pets", null);
		HttpResponse<String> invalid = get("/actuator/health", "invalid request id with spaces");
		HttpResponse<String> excessive = get("/actuator/health", "x".repeat(129));

		assertThat(missing.statusCode()).isEqualTo(403);
		assertThat(missing.headers().firstValue("X-Request-Id")).hasValueSatisfying(
			value -> assertThat(value).matches(GENERATED_REQUEST_ID));
		assertThat(invalid.headers().firstValue("X-Request-Id")).hasValueSatisfying(
			value -> assertThat(value).matches(GENERATED_REQUEST_ID));
		assertThat(excessive.headers().firstValue("X-Request-Id")).hasValueSatisfying(
			value -> assertThat(value).matches(GENERATED_REQUEST_ID));
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
}
