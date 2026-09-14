package com.falapet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
class ApplicationContextIntegrationTest extends PostgreSqlIntegrationTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Test
	void loadsContextAgainstPostgreSqlWithAppliedMigrations() throws Exception {
		assertThat(applicationContext).isNotNull();
		assertThat(dataSource.getConnection().getMetaData().getURL()).startsWith("jdbc:postgresql:");
		assertThat(flyway.info().applied()).hasSize(1);
		assertThat(jdbcTemplate.queryForObject(
			"select metadata_value from application_metadata where metadata_key = 'schema_baseline'",
			String.class)).isEqualTo("1");
	}

	@Test
	void doesNotProvideH2OrTemporaryUsers() {
		assertThat(ClassUtils.isPresent("org.h2.Driver", getClass().getClassLoader())).isFalse();
		assertThat(applicationContext.getBeansOfType(UserDetailsService.class)).isEmpty();
	}

	@Test
	void deniesFutureApiEvenWithAProvisionalTestIdentity() throws Exception {
		MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(springSecurity())
			.build()
			.perform(get("/api/v1/pets").with(user("not-a-real-user")))
			.andExpect(status().isForbidden());
	}
}
