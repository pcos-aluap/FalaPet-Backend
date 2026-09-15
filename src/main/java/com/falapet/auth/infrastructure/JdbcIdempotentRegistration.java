package com.falapet.auth.infrastructure;

import java.security.SecureRandom;
import java.util.function.Supplier;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.falapet.auth.application.AuthService;
import com.falapet.auth.application.IdempotentRegistration;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

import tools.jackson.databind.ObjectMapper;

@Component
final class JdbcIdempotentRegistration implements IdempotentRegistration {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final CryptographicTokens hashes;
    private final ObjectMapper json;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    JdbcIdempotentRegistration(JdbcTemplate jdbc, DataSource dataSource, CryptographicTokens hashes,
            ObjectMapper json, @Value("${falapet.auth.idempotency-encryption-key}") String encryptionKey) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.hashes = hashes;
        this.json = json;
        byte[] bytes = encryptionKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length != 32) {
            throw new IllegalStateException("Registration idempotency encryption key must be 32 bytes");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public AuthService.AccountSession execute(String email, String idempotencyKey,
            String requestFingerprint, Supplier<AuthService.AccountSession> registration) {
        String subjectHash = hashes.hash(email);
        String keyHash = hashes.hash(idempotencyKey);
        return transactions.execute(status -> {
            int inserted = jdbc.update("""
                    INSERT INTO auth_registration_idempotency (subject_hash, key_hash, request_hash)
                    VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                    """, subjectHash, keyHash, requestFingerprint);
            if (inserted == 1) {
                AuthService.AccountSession response = registration.get();
                byte[] encrypted = encrypt(json.writeValueAsBytes(response));
                jdbc.update("""
                        UPDATE auth_registration_idempotency SET encrypted_response = ?
                        WHERE subject_hash = ? AND key_hash = ?
                        """, encrypted, subjectHash, keyHash);
                return response;
            }
            return jdbc.queryForObject("""
                    SELECT request_hash, encrypted_response
                    FROM auth_registration_idempotency
                    WHERE subject_hash = ? AND key_hash = ? FOR UPDATE
                    """, (rs, row) -> {
                if (!requestFingerprint.equals(rs.getString("request_hash"))) {
                    throw new ContractException(ErrorCode.DUPLICATE_RESOURCE);
                }
                byte[] ciphertext = rs.getBytes("encrypted_response");
                if (ciphertext == null) {
                    throw new ContractException(ErrorCode.SERVICE_UNAVAILABLE);
                }
                return json.readValue(decrypt(ciphertext), AuthService.AccountSession.class);
            }, subjectHash, keyHash);
        });
    }

    private byte[] encrypt(byte[] plaintext) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] result = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Registration response encryption unavailable", exception);
        }
    }

    private byte[] decrypt(byte[] encrypted) {
        try {
            if (encrypted.length < 29) {
                throw new IllegalStateException("Registration response ciphertext is invalid");
            }
            byte[] nonce = java.util.Arrays.copyOfRange(encrypted, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(encrypted, 12, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return cipher.doFinal(ciphertext);
        } catch (Exception exception) {
            throw new IllegalStateException("Registration response decryption unavailable", exception);
        }
    }
}
