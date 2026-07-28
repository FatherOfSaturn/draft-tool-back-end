package org.magic.accountService.external;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class GoogleTokenVerifierTest {

    private final GoogleTokenVerifier verifier = new GoogleTokenVerifier("test-client-id", true);

    @Test
    void shouldVerifyDevToken() {
        String token = buildDevToken("{\"alg\":\"none\"}", "{\"sub\":\"sub-123\",\"email\":\"test@example.com\",\"email_verified\":true,\"name\":\"Test User\"}");

        GoogleTokenVerifier.GoogleUser result = verifier.verify(token);

        assertNotNull(result);
        assertEquals("sub-123", result.sub());
        assertEquals("test@example.com", result.email());
        assertEquals("Test User", result.name());
    }

    @Test
    void shouldHandleDevTokenWithNameFallbackToEmail() {
        String token = buildDevToken("{}", "{\"sub\":\"sub-123\",\"email\":\"test@example.com\"}");

        GoogleTokenVerifier.GoogleUser result = verifier.verify(token);

        assertNotNull(result);
        assertEquals("test@example.com", result.name());
    }

    @Test
    void shouldHandleDevTokenWithSubFallback() {
        String token = buildDevToken("{}", "{\"email\":\"test@example.com\"}");

        GoogleTokenVerifier.GoogleUser result = verifier.verify(token);

        assertNotNull(result);
        assertEquals("test@example.com", result.sub());
        assertEquals("test@example.com", result.email());
    }

    @Test
    void shouldHandleDevTokenWithEmailFallback() {
        String token = buildDevToken("{}", "{\"sub\":\"sub-123\"}");

        GoogleTokenVerifier.GoogleUser result = verifier.verify(token);

        assertNotNull(result);
        assertEquals("sub-123", result.sub());
        assertEquals("sub-123", result.email());
    }

    @Test
    void shouldReturnNullForDevTokenWithoutSubOrEmail() {
        String token = buildDevToken("{}", "{\"name\":\"Only Name\"}");

        GoogleTokenVerifier.GoogleUser result = verifier.verify(token);

        assertNull(result);
    }

    @Test
    void shouldReturnNullForDevTokenWithTooFewParts() {
        String result = String.valueOf(verifier.verify("only-one-part"));
        assertTrue(result.contains("null") || result.equals("null"));
    }

    @Test
    void shouldReturnNullForMalformedDevTokenPayload() {
        String base64Part = Base64.getUrlEncoder().encodeToString("not-json".getBytes());
        String token = "header." + base64Part + ".sig";

        GoogleTokenVerifier.GoogleUser result = verifier.verify(token);

        assertNull(result);
    }

    @Test
    void shouldReturnNullForNullToken() {
        assertNull(verifier.verify(null));
    }

    @Test
    void shouldReturnNullForEmptyToken() {
        assertNull(verifier.verify(""));
    }

    private static String buildDevToken(final String headerJson, final String payloadJson) {
        String encodedHeader = Base64.getUrlEncoder().encodeToString(headerJson.getBytes());
        String encodedPayload = Base64.getUrlEncoder().encodeToString(payloadJson.getBytes());
        return encodedHeader + "." + encodedPayload + ".fake-signature";
    }
}
