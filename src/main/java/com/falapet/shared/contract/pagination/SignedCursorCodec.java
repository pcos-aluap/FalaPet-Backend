package com.falapet.shared.contract.pagination;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SignedCursorCodec {

	private static final byte FORMAT_VERSION = 1;
	private static final int SIGNATURE_BYTES = 32;
	private static final int MAX_TOKEN_LENGTH = 4096;
	private static final int MAX_COMPONENTS = 16;
	private static final int MAX_COMPONENT_BYTES = 512;

	private final SecretKeySpec signingKey;

	public SignedCursorCodec(String signingSecret) {
		byte[] secret = signingSecret.getBytes(StandardCharsets.UTF_8);
		if (secret.length < 32) {
			throw new IllegalArgumentException("Cursor signing key must contain at least 32 UTF-8 bytes");
		}
		this.signingKey = new SecretKeySpec(secret, "HmacSHA256");
	}

	public String encode(CursorState state) {
		try {
			byte[] payload = serialize(state);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
				+ "."
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
		} catch (IOException exception) {
			throw new IllegalStateException("Could not encode cursor", exception);
		}
	}

	public CursorState decode(String cursor) {
		if (cursor == null || cursor.isBlank() || cursor.length() > MAX_TOKEN_LENGTH) {
			throw new InvalidCursorException();
		}
		try {
			String[] parts = cursor.split("\\.", -1);
			if (parts.length != 2) {
				throw new InvalidCursorException();
			}
			byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
			byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
			if (signature.length != SIGNATURE_BYTES || !MessageDigest.isEqual(signature, sign(payload))) {
				throw new InvalidCursorException();
			}
			return deserialize(payload);
		} catch (IllegalArgumentException | IOException exception) {
			if (exception instanceof InvalidCursorException invalidCursorException) {
				throw invalidCursorException;
			}
			throw new InvalidCursorException();
		}
	}

	private byte[] serialize(CursorState state) throws IOException {
		if (state.position().size() > MAX_COMPONENTS) {
			throw new IllegalArgumentException("Cursor has too many position components");
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeByte(FORMAT_VERSION);
			writeComponent(output, state.scope());
			writeComponent(output, state.filterFingerprint());
			output.writeByte(state.position().size());
			for (String component : state.position()) {
				writeComponent(output, component);
			}
		}
		return bytes.toByteArray();
	}

	private CursorState deserialize(byte[] payload) throws IOException {
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
			if (input.readUnsignedByte() != FORMAT_VERSION) {
				throw new InvalidCursorException();
			}
			String scope = readComponent(input);
			String filterFingerprint = readComponent(input);
			int size = input.readUnsignedByte();
			if (size > MAX_COMPONENTS) {
				throw new InvalidCursorException();
			}
			List<String> position = new ArrayList<>(size);
			for (int index = 0; index < size; index++) {
				position.add(readComponent(input));
			}
			if (input.available() != 0) {
				throw new InvalidCursorException();
			}
			return new CursorState(scope, filterFingerprint, position);
		}
	}

	private void writeComponent(DataOutputStream output, String value) throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if (bytes.length == 0 || bytes.length > MAX_COMPONENT_BYTES) {
			throw new IllegalArgumentException("Invalid cursor component length");
		}
		output.writeShort(bytes.length);
		output.write(bytes);
	}

	private String readComponent(DataInputStream input) throws IOException {
		int length = input.readUnsignedShort();
		if (length == 0 || length > MAX_COMPONENT_BYTES || length > input.available()) {
			throw new InvalidCursorException();
		}
		return new String(input.readNBytes(length), StandardCharsets.UTF_8);
	}

	private byte[] sign(byte[] payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(signingKey);
			return mac.doFinal(payload);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("HmacSHA256 is not available", exception);
		}
	}
}
