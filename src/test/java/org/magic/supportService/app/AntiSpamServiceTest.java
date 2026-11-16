package org.magic.supportService.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.magic.common.RateLimiter;
import org.magic.supportService.api.CreateSupportRequest;
import org.magic.supportService.api.SupportPriority;
import org.magic.supportService.api.SupportType;
import org.magic.supportService.external.TurnstileVerifier;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class AntiSpamServiceTest {

    @Mock
    FormTokenService formTokenService;

    @Mock
    TurnstileVerifier turnstileVerifier;

    private RateLimiter rateLimiter;
    private SimpleMeterRegistry meterRegistry;

    private AntiSpamService service;

    @BeforeEach
    void setup() {
        rateLimiter = new RateLimiter();
        meterRegistry = new SimpleMeterRegistry();
        service = new AntiSpamService("off", formTokenService, turnstileVerifier, rateLimiter,
                meterRegistry, true, 2, Duration.ofHours(1));
    }

    private CreateSupportRequest request(final String website) {
        return new CreateSupportRequest("Title", "Description", "user@example.com",
                SupportPriority.MEDIUM, website, null, null, SupportType.MISC_SUPPORT);
    }

    @Test
    void shouldDetectHoneypotAndIncrementCounter() {
        assertFalse(service.isHoneypot(request(null)));
        assertFalse(service.isHoneypot(request("")));
        assertFalse(service.isHoneypot(request("   ")));
        assertEquals(0.0, meterRegistry.counter("support.honeypot.triggered").count());

        assertTrue(service.isHoneypot(request("http://spam.example")));
        assertTrue(service.isHoneypot(request("x")));
        assertEquals(2.0, meterRegistry.counter("support.honeypot.triggered").count());
    }

    @Test
    void shouldSkipChecksInOffMode() {
        var uni = service.prePersistChecks(request(null), "203.0.113.5");
        assertDoesNotThrow(() -> uni.await().indefinitely());
    }

    @Test
    void shouldRejectFormTokenWhenEnforced() {
        service = new AntiSpamService("full", formTokenService, turnstileVerifier, rateLimiter,
                meterRegistry, true, 2, Duration.ofHours(1));
        doThrow(new WebApplicationException(Response.status(400).build()))
                .when(formTokenService).verify(any());

        var failing = service.prePersistChecks(request(null), "203.0.113.5");

        var e = assertThrows(WebApplicationException.class,
                () -> failing.await().indefinitely());
        assertEquals(400, e.getResponse().getStatus());
        assertEquals(1.0, meterRegistry.counter("support.spam.rejected", "reason", "form-token").count());
    }

    @Test
    void shouldNotRejectInReportOnlyMode() {
        service = new AntiSpamService("report-only", formTokenService, turnstileVerifier, rateLimiter,
                meterRegistry, true, 2, Duration.ofHours(1));
        doThrow(new WebApplicationException(Response.status(400).build()))
                .when(formTokenService).verify(any());

        var uni = service.prePersistChecks(request(null), "203.0.113.5");

        assertDoesNotThrow(() -> uni.await().indefinitely());
        assertEquals(1.0, meterRegistry.counter("support.spam.report_only", "reason", "form-token").count());
    }

    @Test
    void shouldRejectEmailRateLimitWith429() {
        CreateSupportRequest req = request(null);

        service.checkEmailRate(req.contactEmail());
        service.checkEmailRate(req.contactEmail());

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> service.checkEmailRate(req.contactEmail()));
        assertEquals(429, e.getResponse().getStatus());
        assertEquals("3600", e.getResponse().getHeaderString("Retry-After"));
    }

    @Test
    void shouldCountGmailAliasesAsOneIdentity() {
        service.checkEmailRate("j.o.e@gmail.com");
        service.checkEmailRate("joe+tag@googlemail.com");

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> service.checkEmailRate("JOE@gmail.com"));
        assertEquals(429, e.getResponse().getStatus());
    }

    @Test
    void shouldSkipEmailLimitWhenDisabled() {
        service = new AntiSpamService("off", formTokenService, turnstileVerifier, rateLimiter,
                meterRegistry, false, 2, Duration.ofHours(1));

        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> service.checkEmailRate("user@example.com"));
        }
    }
}