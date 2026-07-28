package org.magic.common.admin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AdminServiceTest {

    @Test
    void shouldConsiderWhitelistedEmailAsAdmin() {
        AdminService service = new AdminService("admin@example.com,admin2@test.com");
        assertTrue(service.isAdmin("admin@example.com"));
        assertTrue(service.isAdmin("admin2@test.com"));
    }

    @Test
    void shouldBeCaseInsensitive() {
        AdminService service = new AdminService("Admin@Example.com");
        assertTrue(service.isAdmin("admin@example.com"));
        assertTrue(service.isAdmin("ADMIN@EXAMPLE.COM"));
    }

    @Test
    void shouldTrimWhitespace() {
        AdminService service = new AdminService("  admin@example.com ,  admin2@test.com  ");
        assertTrue(service.isAdmin("admin@example.com"));
        assertTrue(service.isAdmin("admin2@test.com"));
    }

    @Test
    void shouldNotConsiderNonAdminEmail() {
        AdminService service = new AdminService("admin@example.com");
        assertFalse(service.isAdmin("other@example.com"));
    }

    @Test
    void shouldHandleNullEmail() {
        AdminService service = new AdminService("admin@example.com");
        assertFalse(service.isAdmin(null));
    }

    @Test
    void shouldHandleBlankEmail() {
        AdminService service = new AdminService("admin@example.com");
        assertFalse(service.isAdmin(""));
        assertFalse(service.isAdmin("   "));
    }

    @Test
    void shouldHandleEmptyConfig() {
        AdminService service = new AdminService("");
        assertFalse(service.isAdmin("admin@example.com"));
    }

    @Test
    void shouldHandleConfigWithOnlyCommas() {
        AdminService service = new AdminService(",,,");
        assertFalse(service.isAdmin("admin@example.com"));
    }
}
