package org.magic.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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

        Response response = filter.filter(requestContext);

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeaderString("Retry-After"));
    }

    @Test
    void shouldAbortWith429WhenScryfallOverLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("scryfall/cards/batch/cubecobra");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(rateLimiter.isAllowed(eq("203.0.113.9"), eq(120), any())).thenReturn(false);

        Response response = filter.filter(requestContext);

        assertEquals(429, response.getStatus());
    }

    @Test
    void shouldAbortWith429WhenLobbyOverLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("lobby/ABC12345/join");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(rateLimiter.isAllowed(eq("203.0.113.9"), eq(60), any())).thenReturn(false);

        Response response = filter.filter(requestContext);

        assertEquals(429, response.getStatus());
    }

    @Test
    void shouldAbortWith429WhenSupportBurstOverLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("support");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("POST");
        when(rateLimiter.isAllowed(eq("203.0.113.9#0"), anyInt(), any())).thenReturn(false);

        Response response = filter.filter(requestContext);

        assertEquals(429, response.getStatus());
    }

    @Test
    void shouldAbortWith429WhenSupportHourlyOverLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("support");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("POST");
        when(rateLimiter.isAllowed(eq("203.0.113.9#0"), anyInt(), any())).thenReturn(true);
        when(rateLimiter.isAllowed(eq("203.0.113.9#1"), anyInt(), any())).thenReturn(false);

        Response response = filter.filter(requestContext);

        assertEquals(429, response.getStatus());
        assertEquals("3600", response.getHeaderString("Retry-After"));
    }

    @Test
    void shouldAllowSupportCreateWhenBothTiersUnderLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("support");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("POST");
        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(true);

        Response response = filter.filter(requestContext);

        assertNull(response);
        verify(rateLimiter).isAllowed(eq("203.0.113.9#0"), eq(3), eq(Duration.ofSeconds(60)));
        verify(rateLimiter).isAllowed(eq("203.0.113.9#1"), eq(5), eq(Duration.ofHours(1)));
    }

    @Test
    void shouldLimitAnonymousSupportReads() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("support/public");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("GET");
        when(rateLimiter.isAllowed(eq("203.0.113.9"), eq(60), any())).thenReturn(false);

        Response response = filter.filter(requestContext);

        assertEquals(429, response.getStatus());
    }

    @Test
    void shouldLimitSupportByIdRead() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("support/ABC123");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("GET");
        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(true);

        Response response = filter.filter(requestContext);

        assertNull(response);
        verify(rateLimiter).isAllowed(eq("203.0.113.9"), eq(60), eq(Duration.ofSeconds(60)));
    }

    @Test
    void shouldBucketAuthenticatedEndpointsPerSubject() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("account/decks");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        when(requestContext.getHeaderString("Authorization"))
                .thenReturn("Bearer " + token("google-sub-123"));
        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(true);

        Response response = filter.filter(requestContext);

        assertNull(response);
        verify(rateLimiter).isAllowed(eq("user:google-sub-123"), eq(300), eq(Duration.ofSeconds(60)));
    }

    @Test
    void shouldFallBackToIpWhenNoTokenPresent() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("account/decks");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(true);

        Response response = filter.filter(requestContext);

        assertNull(response);
        verify(rateLimiter).isAllowed(eq("anon:203.0.113.9"), eq(300), eq(Duration.ofSeconds(60)));
    }

    @Test
    void shouldLimitAdminEndpointsPerSubject() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("admin/stats/drafts/classic");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getHeaderString("Authorization"))
                .thenReturn("Bearer " + token("admin-sub-9"));
        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(false);

        Response response = filter.filter(requestContext);

        assertEquals(429, response.getStatus());
        verify(rateLimiter).isAllowed(eq("user:admin-sub-9"), anyInt(), any());
    }

    @Test
    void shouldBucketLoginByIpEvenWithToken() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("account/login");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(true);

        filter.filter(requestContext);

        verify(rateLimiter).isAllowed(eq("203.0.113.9"), eq(20), any());
    }

    @Test
    void shouldNotLimitSupportFormTokenEndpoint() throws Exception {
        when(uriInfo.getPath()).thenReturn("support/form-token");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("GET");
        remote("203.0.113.9");
        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(true);

        Response response = filter.filter(requestContext);

        assertNull(response);
        verify(rateLimiter).isAllowed(eq("203.0.113.9"), eq(60), eq(Duration.ofSeconds(60)));
    }

    @Test
    void shouldNotRateLimitUnparseableTokenBySubject() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("account/decks");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getHeaderString("Authorization")).thenReturn("Bearer not-a-jwt");
        when(rateLimiter.isAllowed(anyString(), anyInt(), any())).thenReturn(true);

        filter.filter(requestContext);

        verify(rateLimiter).isAllowed(eq("anon:203.0.113.9"), eq(300), any());
    }

    /** Builds an unsigned JWT-shaped token whose payload carries the given sub. */
    private static String token(final String subject) {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + subject + "\",\"iss\":\"magic-draft\"}");
        return header + "." + payload + ".signature";
    }

    private static String base64Url(final String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void shouldAbortWith429WhenGameDraftActionOverLimit() throws Exception {
        remote("203.0.113.9");
        when(uriInfo.getPath()).thenReturn("classic-game/game-1/draftCheck");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(requestContext.getMethod()).thenReturn("GET");
        when(rateLimiter.isAllowed(eq("203.0.113.9"), eq(120), any())).thenReturn(false);

        Response response = filter.filter(requestContext);

        assertEquals(429, response.getStatus());
    }

    @Test
    void shouldNotLimitUnrelatedEndpoints() throws Exception {
        when(uriInfo.getPath()).thenReturn("health");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);

        Response response = filter.filter(requestContext);

        assertNull(response);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void shouldSkipEntirelyWhenDisabled() throws Exception {
        filter = newFilter(false, false, "");

        Response response = filter.filter(requestContext);

        assertNull(response);
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

        Response response = filter.filter(requestContext);

        assertEquals(429, response.getStatus());
    }

    @Test
    void shouldIgnoreForwardedHeaderFromUntrustedPeer() throws Exception {
        filter = newFilter(true, true, "10.0.0.1");
        remote("203.0.113.99");
        when(uriInfo.getPath()).thenReturn("account/login");
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(rateLimiter.isAllowed(eq("203.0.113.99"), eq(20), any())).thenReturn(true);

        Response response = filter.filter(requestContext);

        assertNull(response);
        verify(requestContext, never()).getHeaders();
    }

    private RateLimitFilter newFilter(final boolean enabled, final boolean trustForwarded,
                                      final String trustedProxies) {
        return new RateLimitFilter(rateLimiter, new ClientIpResolver(trustForwarded, Optional.ofNullable(trustedProxies)),
                enabled,
                20, Duration.ofSeconds(60),
                120, Duration.ofSeconds(60),
                60, Duration.ofSeconds(60),
                3, Duration.ofSeconds(60),
                5, Duration.ofHours(1),
                60, Duration.ofSeconds(60),
                300, Duration.ofSeconds(60),
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
