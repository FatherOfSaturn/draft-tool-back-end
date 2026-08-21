package org.magic.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    RateLimiter rateLimiter;

    @Mock
    ContainerRequestContext requestContext;

    @Mock
    UriInfo uriInfo;

    RateLimitFilter filter;

    @BeforeEach
    void setup() {
        filter = newFilter(true, false, "");
    }

    @Test
    void shouldAbortWith429WhenLoginOverLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("account/login");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(rateLimiter.isAllowed(eq("203.0.113.9"), eq(20), any())).thenReturn(false);

        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> {
            assertEquals(429, response.getStatus());
            assertEquals("60", response.getHeaderString("Retry-After"));
            return true;
        }));
    }

    @Test
    void shouldAbortWith429WhenScryfallOverLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("scryfall/cards/batch/cubecobra");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(rateLimiter.isAllowed(eq("203.0.113.9"), eq(120), any())).thenReturn(false);

        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> response.getStatus() == 429));
    }

    @Test
    void shouldAbortWith429WhenLobbyOverLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("lobby/ABC12345/join");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(rateLimiter.isAllowed(eq("203.0.113.9"), eq(60), any())).thenReturn(false);

        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> response.getStatus() == 429));
    }

    @Test
    void shouldAbortWith429WhenSupportCreateOverLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("support");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("POST");
        when(rateLimiter.isAllowed(eq("203.0.113.9"), eq(20), any())).thenReturn(false);

        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> response.getStatus() == 429));
    }

    @Test
    void shouldNotLimitSupportReads() throws Exception {
        when(uriInfo.getPath()).thenReturn("support/ABC123");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("GET");

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void shouldAbortWith429WhenGameDraftActionOverLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("classic-game/game-1/draftCheck");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("GET");
        when(rateLimiter.isAllowed(eq("203.0.113.9"), eq(120), any())).thenReturn(false);

        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> response.getStatus() == 429));
    }

    @Test
    void shouldNotLimitUnrelatedEndpoints() throws Exception {
        when(uriInfo.getPath()).thenReturn("account");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void shouldSkipEntirelyWhenDisabled() throws Exception {
        filter = newFilter(false, false, "");

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verifyNoInteractions(rateLimiter, uriInfo);
    }

    @Test
    void shouldHonorForwardedHeaderOnlyFromTrustedProxy() throws Exception {
        filter = newFilter(true, true, "10.0.0.1");
        remote("10.0.0.1");
        when(uriInfo.getPath()).thenReturn("account/login");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getHeaders()).thenReturn(forwardedHeaders("203.0.113.9"));
        when(rateLimiter.isAllowed(eq("203.0.113.9"), eq(20), any())).thenReturn(false);

        filter.filter(requestContext);

        verify(requestContext).abortWith(argThat(response -> response.getStatus() == 429));
    }

    @Test
    void shouldIgnoreForwardedHeaderFromUntrustedPeer() throws Exception {
        filter = newFilter(true, true, "10.0.0.1");
        remote("203.0.113.99");
        when(uriInfo.getPath()).thenReturn("account/login");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(rateLimiter.isAllowed(eq("203.0.113.99"), eq(20), any())).thenReturn(true);

        filter.filter(requestContext);

        verify(requestContext, never()).abortWith(any());
        verify(requestContext, never()).getHeaders();
    }

    private RateLimitFilter newFilter(final boolean enabled, final boolean trustForwarded,
                                      final String trustedProxies) {
        return new RateLimitFilter(rateLimiter, enabled, trustForwarded,
                Optional.ofNullable(trustedProxies),
                20, Duration.ofSeconds(60),
                120, Duration.ofSeconds(60),
                60, Duration.ofSeconds(60),
                20, Duration.ofSeconds(60),
                120, Duration.ofSeconds(60));
    }

    private void remote(final String address) {
        when(requestContext.getProperty("quarkus.remote-address")).thenReturn(address);
    }

    private MultivaluedMap<String, String> forwardedHeaders(final String clientIp) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.put("X-Forwarded-For", List.of(clientIp));
        return headers;
    }
}
