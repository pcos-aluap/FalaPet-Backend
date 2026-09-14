package com.falapet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularArchitectureTest {

	private final ApplicationModules modules = ApplicationModules.of(FalaPetApplication.class);

	@Test
	void verifiesModuleBoundariesAndAbsenceOfCycles() {
		modules.verify();

		Set<String> moduleNames = modules.stream()
			.map(module -> module.getIdentifier().toString())
			.collect(Collectors.toSet());
		assertThat(moduleNames).containsExactlyInAnyOrder(
			"auth", "user", "pet", "device", "central", "button", "event",
			"audio", "context", "training", "sync", "insight", "shared");
	}
}
