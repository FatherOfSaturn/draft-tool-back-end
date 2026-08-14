package org.magic.common;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class TokenRevocationServiceTest {

    @Test
    void shouldRevokeToken() {
        TokenRevocationService service = new TokenRevocationService();

        service.revoke("token-1");

        assertTrue(service.isRevoked("token-1"));
    }

    @Test
    void shouldNotRevokeOtherTokens() {
        TokenRevocationService service = new TokenRevocationService();

        service.revoke("token-1");

        assertFalse(service.isRevoked("token-2"));
    }

    @Test
    void shouldBeIdempotent() {
        TokenRevocationService service = new TokenRevocationService();

        service.revoke("token-1");
        service.revoke("token-1");

        assertTrue(service.isRevoked("token-1"));
    }

    @Test
    void shouldIgnoreBlankTokens() {
        TokenRevocationService service = new TokenRevocationService();

        service.revoke(null);
        service.revoke("   ");

        assertFalse(service.isRevoked(null));
        assertFalse(service.isRevoked("   "));
    }

    @Test
    void shouldPruneExpiredRevocations() throws Exception {
        TokenRevocationService service = new TokenRevocationService(Duration.ZERO);

        service.revoke("expired-token");
        service.revoke("fresh-token");
        Thread.sleep(5);

        service.cleanupExpired();

        assertFalse(service.isRevoked("expired-token"));
        assertFalse(service.isRevoked("fresh-token"));
    }
}
