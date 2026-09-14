package com.falapet;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
@Testcontainers
abstract class PostgreSqlIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6-alpine")
		.withDatabaseName("falapet_test")
		.withUsername("falapet_test")
		.withPassword("falapet_test")
		.withEnv("TZ", "UTC");

	@BeforeAll
	static void verifyFreshDatabase() {
		if (!POSTGRES.isRunning()) {
			throw new IllegalStateException("PostgreSQL Testcontainer was not started");
		}
	}
}
