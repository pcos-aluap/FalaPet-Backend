package com.falapet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.falapet.auth.application.AuthRateLimiter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"falapet.auth.rate.max-attempts=2", "falapet.auth.rate.window=PT1S"})
class AuthRateLimiterIntegrationTest extends PostgreSqlIntegrationTest {
    @Autowired private AuthRateLimiter limiter;
    @LocalServerPort private int port;

    @Test
    void exhaustionRecoversAfterWindowAndConcurrentAttemptsNeverExceedLimit() throws Exception {
        String subject = "rate-" + UUID.randomUUID();
        assertThat(limiter.retryAfterSeconds("login", subject)).isZero();
        assertThat(limiter.retryAfterSeconds("login", subject)).isZero();
        assertThat(limiter.retryAfterSeconds("login", subject)).isPositive();
        Thread.sleep(Duration.ofMillis(1200));
        assertThat(limiter.retryAfterSeconds("login", subject)).isZero();

        String concurrent = "concurrent-" + UUID.randomUUID();
        List<Callable<Long>> calls = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> (Callable<Long>) () -> limiter.retryAfterSeconds("refresh", concurrent))
                .toList();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = executor.invokeAll(calls).stream().map(future -> {
                try { return future.get(); } catch (Exception exception) { throw new AssertionError(exception); }
            }).toList();
            assertThat(results).filteredOn(value -> value == 0).hasSize(2);
            assertThat(results).filteredOn(value -> value > 0).hasSize(6);
        }
    }

    @Test
    void returnsContract429WithRetryAfterAndRecoversWhenWindowEnds() throws Exception {
        String email = "rate-http-" + UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"long-password-123456\"}";
        assertThat(login(body).statusCode()).isEqualTo(401);
        assertThat(login(body).statusCode()).isEqualTo(401);
        HttpResponse<String> limited = login(body);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.body()).contains("RATE_LIMITED", "\"retryable\":true");
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        assertThat(Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow())).isPositive();
        Thread.sleep(Duration.ofMillis(1200));
        assertThat(login(body).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> login(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(20))
                .header("X-Request-Id", "rate-test")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
