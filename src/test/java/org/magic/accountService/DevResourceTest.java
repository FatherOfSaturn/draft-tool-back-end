package org.magic.accountService;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

class DevResourceTest {

    private final DevResource resource = new DevResource();

    @Test
    void shouldGenerateTokenWithDefaults() {
        Response response = resource.generateDevToken(null, null, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("dev-user-123", body.get("sub"));
        assertEquals("dev@example.com", body.get("email"));
        assertEquals("Dev User", body.get("name"));
        assertNotNull(body.get("idToken"));
        assertTrue(((String) body.get("idToken")).contains("."));
    }

    @Test
    void shouldGenerateTokenWithCustomParams() {
        Response response = resource.generateDevToken("custom-sub", "custom@example.com", "Custom Name");

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("custom-sub", body.get("sub"));
        assertEquals("custom@example.com", body.get("email"));
        assertEquals("Custom Name", body.get("name"));
    }

    @Test
    void shouldGenerateTokenWithPartialParams() {
        Response response = resource.generateDevToken("only-sub", null, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("only-sub", body.get("sub"));
        assertEquals("dev@example.com", body.get("email"));
        assertEquals("Dev User", body.get("name"));
    }
}
