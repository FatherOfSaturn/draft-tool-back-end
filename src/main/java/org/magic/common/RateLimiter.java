package org.magic.common;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Simple in-memory sliding-window rate limiter keyed by an arbitrary client
 * identifier (typically the client IP). Tracks request timestamps per key and
 * rejects once the number of requests in the window reaches the configured limit.
 *
 * <p>The limiter is process-local: it is a coarse defense against abuse (brute
 * force, cost amplification) rather than an authoritative quota. It never throws
 * and fails open when the key table is full, so a memory exhaustion attack cannot
 * take the service down.</p>
 */
@ApplicationScoped
public class RateLimiter {

    private static final int MAX_KEYS = 10_000;

    private final Map<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * Records a request for the given key and returns whether it is within the limit.
     *
     * @param key        the client identifier
     * @param maxRequests the maximum number of requests allowed within the window
     * @param window     the sliding window duration
     * @return {@code true} if the request is allowed, {@code false} if rate-limited
     */
    public boolean isAllowed(final String key, final int maxRequests, final Duration window) {
        if (key == null || key.isBlank() || maxRequests <= 0) {
            return true;
        }

        if (!hits.containsKey(key) && hits.size() >= MAX_KEYS) {
            return true;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - window.toMillis();

        ArrayDeque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /**
     * Periodically prunes keys that have been idle for several minutes so the
     * key table does not grow without bound.
     */
    @Scheduled(every = "PT1M")
    void cleanupStaleKeys() {
        long cutoff = System.currentTimeMillis() - Duration.ofMinutes(10).toMillis();
        hits.forEach((key, deque) -> {
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                    deque.pollFirst();
                }
                if (deque.isEmpty()) {
                    hits.remove(key, deque);
                }
            }
        });
    }
}
