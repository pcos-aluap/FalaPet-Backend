package com.falapet.auth.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.falapet.auth.application.AuthRateLimiter;

@Component
final class JdbcAuthRateLimiter implements AuthRateLimiter {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final CryptographicTokens tokens;
    private final Duration window;
    private final int maxAttempts;

    JdbcAuthRateLimiter(JdbcTemplate jdbc, DataSource dataSource, CryptographicTokens tokens,
            @Value("${falapet.auth.rate.window}") Duration window,
            @Value("${falapet.auth.rate.max-attempts}") int maxAttempts) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.tokens = tokens;
        this.window = window;
        this.maxAttempts = maxAttempts;
        if (window.isNegative() || window.isZero() || window.compareTo(Duration.ofHours(1)) > 0
                || maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalStateException("Authentication rate limit configuration is unsafe");
        }
    }

    @Override
    public long retryAfterSeconds(String operation, String subject) {
        String subjectHash = tokens.hash(subject);
        Instant now = Instant.now();
        return transactions.execute(status -> {
            int inserted = jdbc.update("""
                    INSERT INTO auth_rate_bucket (operation, subject_hash, window_started_at, attempts)
                    VALUES (?, ?, ?, 1) ON CONFLICT DO NOTHING
                    """, operation, subjectHash, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            if (inserted == 1) {
                return 0L;
            }
            var bucket = jdbc.queryForObject("""
                    SELECT window_started_at, attempts FROM auth_rate_bucket
                    WHERE operation = ? AND subject_hash = ? FOR UPDATE
                    """, (rs, row) -> new Bucket(rs.getTimestamp("window_started_at").toInstant(),
                    rs.getInt("attempts")), operation, subjectHash);
            if (bucket == null) {
                throw new IllegalStateException("Authentication rate bucket was not created");
            }
            Instant end = bucket.startedAt().plus(window);
            if (!end.isAfter(now)) {
                jdbc.update("""
                        UPDATE auth_rate_bucket SET window_started_at = ?, attempts = 1
                        WHERE operation = ? AND subject_hash = ?
                        """, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), operation, subjectHash);
                return 0L;
            }
            if (bucket.attempts() >= maxAttempts) {
                return Math.max(1L, Duration.between(now, end).toSeconds() + 1);
            }
            jdbc.update("""
                    UPDATE auth_rate_bucket SET attempts = attempts + 1
                    WHERE operation = ? AND subject_hash = ?
                    """, operation, subjectHash);
            return 0L;
        });
    }

    private record Bucket(Instant startedAt, int attempts) {}
}
