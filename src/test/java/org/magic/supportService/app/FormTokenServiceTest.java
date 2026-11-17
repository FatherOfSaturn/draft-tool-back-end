package org.magic.supportService.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

class FormTokenServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-16-chars";

    private FormTokenService service;

    @BeforeEach
    void setup() {
        service = new FormTokenService(java.util.Optional.of(SECRET), Duration.ZERO, Duration.ofHours(2));
    }

    @Test
    void shouldIssueAndVerifyToken() {
        String token = service.issue();

        assertNotNull(token);
        assertDoesNotThrow(() -> service.verify(token));
    }

    @Test
    void shouldRejectMissingToken() {
        WebApplicationException e = assertThrows(WebApplicationException.class, () -> service.verify(null));
        assertEquals(400, e.getResponse().getStatus());

        e = assertThrows(WebApplicationException.class, () -> service.verify("  "));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = service.issue();
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + (parts[1].charAt(0) == 'A' ? "B" : "A") + parts[1].substring(1);

        WebApplicationException e = assertThrows(WebApplicationException.class, () -> service.verify(tampered));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldRejectGarbageTokens() {
        for (String garbage : new String[] { "", "abc", "a.b.c", "not-a-number.aaa", "123.!!!" }) {
            WebApplicationException e = assertThrows(WebApplicationException.class,
                    () -> service.verify(garbage), "token: " + garbage);
            assertEquals(400, e.getResponse().getStatus());
        }
    }

    @Test
    void shouldRejectTokensFromAnotherSecret() {
        FormTokenService other = new FormTokenService(java.util.Optional.of("a-different-secret-at-least-16-chars"),
                Duration.ZERO, Duration.ofHours(2));
        String token = other.issue();

        WebApplicationException e = assertThrows(WebApplicationException.class, () -> service.verify(token));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldRejectInstantSubmits() {
        FormTokenService strict = new FormTokenService(java.util.Optional.of(SECRET), Duration.ofSeconds(3), Duration.ofHours(2));
        String token = strict.issue();

        WebApplicationException e = assertThrows(WebApplicationException.class, () -> strict.verify(token));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldAcceptTokenOlderThanMinAge() throws Exception {
        FormTokenService strict = new FormTokenService(java.util.Optional.of(SECRET), Duration.ofSeconds(3), Duration.ofHours(2));
        String token = strict.issue();
        Thread.sleep(3100);

        assertDoesNotThrow(() -> strict.verify(token));
    }

    @Test
    void shouldRejectExpiredTokens() throws Exception {
        FormTokenService strict = new FormTokenService(java.util.Optional.of(SECRET), Duration.ZERO, Duration.ofSeconds(1));
        String token = strict.issue();
        Thread.sleep(1100);

        WebApplicationException e = assertThrows(WebApplicationException.class, () -> strict.verify(token));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldRejectFutureDatedToken() {
        long future = System.currentTimeMillis() + 60_000;
        String payload = Long.toString(future);
        String forged = payload + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(service.hmac(payload));

        WebApplicationException e = assertThrows(WebApplicationException.class, () -> service.verify(forged));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldFailClosedWhenSecretMissing() {
        FormTokenService unconfigured = new FormTokenService(java.util.Optional.empty(), Duration.ZERO, Duration.ofHours(2));

        WebApplicationException issued = assertThrows(WebApplicationException.class, unconfigured::issue);
        assertEquals(500, issued.getResponse().getStatus());

        WebApplicationException verified = assertThrows(WebApplicationException.class,
                () -> unconfigured.verify("123.abc"));
        assertEquals(500, verified.getResponse().getStatus());
    }
}
