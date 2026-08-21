package org.magic.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-memory store of revoked JWT hashes used to support server-side logout.
 * Tokens are stored as SHA-256 digests (never the raw token) together with a
 * revocation timestamp. Revocations expire once the token could no longer be
 * valid (bounded by the token TTL), after which the entry is pruned.
 *
 * <p>The store is process-local: it supports logout for a single-instance
 * deployment and is a best-effort defense. A truly revoked token remains valid
 * until its natural expiry on any other instance. The table is bounded so a
 * logout flood cannot exhaust memory.</p>
 */
@ApplicationScoped
public class TokenRevocationService {

    private static final int MAX_ENTRIES = 100_000;

    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    private final Duration maxLifetime;

    public TokenRevocationService() {
        this(Duration.ofDays(1));
    }

    TokenRevocationService(final Duration maxLifetime) {
        this.maxLifetime = maxLifetime;
    }

    /**
     * Records a token as revoked. Idempotent; already-revoked tokens stay revoked.
     *
     * @param token the raw JWT (never stored)
     */
    public void revoke(final String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        String hash = hash(token);
        revoked.putIfAbsent(hash, now);
        if (revoked.size() > MAX_ENTRIES) {
            evictOldest();
        }
    }

    /**
     * Returns whether the given token has been revoked.
     *
     * @param token the raw JWT
     * @return {@code true} if the token was previously revoked
     */
    public boolean isRevoked(final String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return revoked.containsKey(hash(token));
    }

    private static String hash(final String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestTs = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : revoked.entrySet()) {
            if (entry.getValue() < oldestTs) {
                oldestTs = entry.getValue();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            revoked.remove(oldestKey);
        }
    }

    /**
     * Periodically prunes revocations older than the token lifetime so the table
     * does not grow without bound.
     */
    @Scheduled(every = "PT5M")
    void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - maxLifetime.toMillis();
        revoked.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
