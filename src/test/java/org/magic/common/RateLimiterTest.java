package org.magic.common;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void shouldAllowRequestsUpToLimit() {
        RateLimiter limiter = new RateLimiter();

        assertTrue(limiter.isAllowed("ip-1", 3, Duration.ofMinutes(1)));
        assertTrue(limiter.isAllowed("ip-1", 3, Duration.ofMinutes(1)));
        assertTrue(limiter.isAllowed("ip-1", 3, Duration.ofMinutes(1)));
    }

    @Test
    void shouldRejectRequestsOverLimit() {
        RateLimiter limiter = new RateLimiter();
        Duration window = Duration.ofMinutes(1);

        for (int i = 0; i < 3; i++) {
            limiter.isAllowed("ip-1", 3, window);
        }

        assertFalse(limiter.isAllowed("ip-1", 3, window));
    }

    @Test
    void shouldTrackClientsIndependently() {
        RateLimiter limiter = new RateLimiter();
        Duration window = Duration.ofMinutes(1);

        for (int i = 0; i < 3; i++) {
            limiter.isAllowed("ip-1", 3, window);
        }

        assertFalse(limiter.isAllowed("ip-1", 3, window));
        assertTrue(limiter.isAllowed("ip-2", 3, window));
    }

    @Test
    void shouldAllowRequestsAgainAfterWindowElapses() throws InterruptedException {
        RateLimiter limiter = new RateLimiter();
        Duration window = Duration.ofMillis(50);

        for (int i = 0; i < 3; i++) {
            limiter.isAllowed("ip-1", 3, window);
        }
        assertFalse(limiter.isAllowed("ip-1", 3, window));

        Thread.sleep(80);
        assertTrue(limiter.isAllowed("ip-1", 3, window));
    }

    @Test
    void shouldFailOpenOnBlankKeyOrNonPositiveLimit() {
        RateLimiter limiter = new RateLimiter();

        assertTrue(limiter.isAllowed(null, 3, Duration.ofMinutes(1)));
        assertTrue(limiter.isAllowed(" ", 3, Duration.ofMinutes(1)));
        assertTrue(limiter.isAllowed("ip-1", 0, Duration.ofMinutes(1)));
        assertTrue(limiter.isAllowed("ip-1", -1, Duration.ofMinutes(1)));
    }
}
