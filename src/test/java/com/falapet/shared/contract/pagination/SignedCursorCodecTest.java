package com.falapet.shared.contract.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class SignedCursorCodecTest {

	private final SignedCursorCodec codec =
		new SignedCursorCodec("test-only-signing-secret-with-at-least-32-bytes");

	@Test
	void createsDeterministicOpaqueCursorAndRestoresStablePosition() {
		CursorState state = new CursorState("button-events", "filters-sha256", List.of("2026-09-14T10:00:00Z", "uuid"));

		String first = codec.encode(state);
		String second = codec.encode(state);

		assertThat(first).isEqualTo(second).doesNotContain("button-events", "2026-09-14");
		assertThat(codec.decode(first)).isEqualTo(state);
	}

	@Test
	void rejectsMalformedAndTamperedCursorWithContractCode() {
		String valid = codec.encode(new CursorState("pets", "filters", List.of("position")));
		String tampered = valid.substring(0, valid.length() - 1) + (valid.endsWith("A") ? "B" : "A");

		assertThatThrownBy(() -> codec.decode("not-a-cursor"))
			.isInstanceOf(InvalidCursorException.class)
			.hasMessageContaining("INVALID_CURSOR");
		assertThatThrownBy(() -> codec.decode(tampered))
			.isInstanceOf(InvalidCursorException.class);
	}

	@Test
	void appliesContractPageLimits() {
		assertThat(CursorPageRequest.of(null, null).limit()).isEqualTo(30);
		assertThat(CursorPageRequest.of("opaque", 100).limit()).isEqualTo(100);
		assertThatThrownBy(() -> CursorPageRequest.of(null, 0))
			.isInstanceOf(com.falapet.shared.contract.http.ContractException.class);
	}
}
