package org.magic.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Account;
import org.magic.accountService.app.AccountDbHandler;
import org.magic.common.admin.AdminService;
import org.magic.common.api.admin.AdminCheckResponse;
import org.magic.common.api.admin.DonationStatsResponse;
import org.magic.common.api.admin.ScryfallCacheStatusResponse;
import org.magic.common.external.ScryfallBulkDataService;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.app.GameCoordination.GameCoordinationWorker;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class AdminResourceTest {

    @Mock
    AdminService adminService;
    @Mock
    AccountDbHandler accountDbHandler;
    @Mock
    GameCoordinationWorker gameWorker;
    @Mock
    ScryfallBulkDataService bulkDataService;

    AdminResource resource;

    @BeforeEach
    void setup() {
        resource = new AdminResource(adminService, accountDbHandler, gameWorker, bulkDataService);
    }

    private Account createAccount(final String email) {
        var account = new Account();
        account.setAccountID("acc-123");
        account.setEmail(email);
        account.setGoogleSub("sub-456");
        account.setDisplayName("Test User");
        account.setDeckIDs(java.util.List.of());
        return account;
    }

    @Test
    void shouldGetDraftStatsWhenAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("admin@example.com")));
        when(adminService.isAdmin("admin@example.com")).thenReturn(true);

        Response response = resource.getDraftStats("acc-123", "pyramid");

        assertEquals(200, response.getStatus());
        assertEquals(50, response.getEntity());
    }

    @Test
    void shouldReturnForbiddenForDraftStatsWhenNotAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("user@example.com")));
        when(adminService.isAdmin("user@example.com")).thenReturn(false);

        Response response = resource.getDraftStats("acc-123", "pyramid");

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldReturnForbiddenForDraftStatsWhenAccountNotFound() {
        when(accountDbHandler.findById("unknown")).thenReturn(Optional.empty());

        Response response = resource.getDraftStats("unknown", "pyramid");

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldGetDonationSummaryWhenAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("admin@example.com")));
        when(adminService.isAdmin("admin@example.com")).thenReturn(true);

        Response response = resource.getDonationSummary("acc-123");

        assertEquals(200, response.getStatus());
        var stats = (DonationStatsResponse) response.getEntity();
        assertEquals(50, stats.totalDonated());
        assertEquals(40, stats.currentMonthly());
    }

    @Test
    void shouldReturnForbiddenForDonationSummaryWhenNotAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("user@example.com")));
        when(adminService.isAdmin("user@example.com")).thenReturn(false);

        Response response = resource.getDonationSummary("acc-123");

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldGetAdminStatusWhenAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("admin@example.com")));
        when(adminService.isAdmin("admin@example.com")).thenReturn(true);

        Response response = resource.getAdminStatus("acc-123");

        assertEquals(200, response.getStatus());
        var check = (AdminCheckResponse) response.getEntity();
        assertTrue(check.isAdmin());
    }

    @Test
    void shouldGetAdminStatusWhenNotAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("user@example.com")));
        when(adminService.isAdmin("user@example.com")).thenReturn(false);

        Response response = resource.getAdminStatus("acc-123");

        assertEquals(200, response.getStatus());
        var check = (AdminCheckResponse) response.getEntity();
        assertFalse(check.isAdmin());
    }

    @Test
    void shouldDeleteGamesWithStatusWhenAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("admin@example.com")));
        when(adminService.isAdmin("admin@example.com")).thenReturn(true);
        when(gameWorker.deleteGamesWithStatus(GameState.GAME_COMPLETE)).thenReturn(Uni.createFrom().item(Response.ok(5).build()));

        Response response = resource.deleteGamesWithStatus("acc-123", "game_complete").await().indefinitely();

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldReturnForbiddenForDeleteGamesWhenNotAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("user@example.com")));
        when(adminService.isAdmin("user@example.com")).thenReturn(false);

        Response response = resource.deleteGamesWithStatus("acc-123", "game_complete").await().indefinitely();

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldGetCacheStatusWhenAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("admin@example.com")));
        when(adminService.isAdmin("admin@example.com")).thenReturn(true);
        when(bulkDataService.getStatus()).thenReturn(
                new ScryfallBulkDataService.CacheStatus(true, 50000, "2026-07-29T21:10:03.341+00:00", "default_cards"));
        when(bulkDataService.isEnabled()).thenReturn(true);

        Response response = resource.getCacheStatus("acc-123");

        assertEquals(200, response.getStatus());
        var status = (ScryfallCacheStatusResponse) response.getEntity();
        assertTrue(status.available());
        assertEquals(50000, status.cardCount());
    }

    @Test
    void shouldReturnForbiddenForCacheStatusWhenNotAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("user@example.com")));
        when(adminService.isAdmin("user@example.com")).thenReturn(false);

        Response response = resource.getCacheStatus("acc-123");

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldTriggerCacheRefreshWhenAdmin() throws Exception {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("admin@example.com")));
        when(adminService.isAdmin("admin@example.com")).thenReturn(true);
        when(bulkDataService.triggerRefresh()).thenReturn(50000);
        when(bulkDataService.getStatus()).thenReturn(
                new ScryfallBulkDataService.CacheStatus(true, 50000, "2026-07-29T21:10:03.341+00:00", "default_cards"));
        when(bulkDataService.isEnabled()).thenReturn(true);

        Response response = resource.triggerCacheRefresh("acc-123");

        assertEquals(200, response.getStatus());
        var status = (ScryfallCacheStatusResponse) response.getEntity();
        assertTrue(status.available());
    }

    @Test
    void shouldReturnForbiddenForCacheTriggerWhenNotAdmin() {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("user@example.com")));
        when(adminService.isAdmin("user@example.com")).thenReturn(false);

        Response response = resource.triggerCacheRefresh("acc-123");

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldHandleCacheRefreshFailure() throws Exception {
        when(accountDbHandler.findById("acc-123")).thenReturn(Optional.of(createAccount("admin@example.com")));
        when(adminService.isAdmin("admin@example.com")).thenReturn(true);
        when(bulkDataService.triggerRefresh()).thenThrow(new java.io.IOException("Network error"));

        Response response = resource.triggerCacheRefresh("acc-123");

        assertEquals(500, response.getStatus());
    }
}
