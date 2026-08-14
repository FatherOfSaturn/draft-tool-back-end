package org.magic.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Account;
import org.magic.accountService.api.Deck;
import org.magic.accountService.app.AccountDbHandler;
import org.magic.accountService.app.DeckDbHandler;
import org.magic.accountService.app.GameHistoryService;
import org.magic.common.api.admin.AdminCheckResponse;
import org.magic.common.api.admin.DonationStatsResponse;
import org.magic.common.api.admin.ScryfallCacheStatusResponse;
import org.magic.common.external.ScryfallBulkDataService;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.app.GameCoordination.GameCoordinationWorker;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class AdminResourceTest {

    @Mock
    GameCoordinationWorker gameWorker;
    @Mock
    ScryfallBulkDataService bulkDataService;
    @Mock
    AccountDbHandler accountDbHandler;
    @Mock
    DeckDbHandler deckDbHandler;
    @Mock
    GameHistoryService gameHistoryService;

    AdminResource resource;

    @BeforeEach
    void setup() {
        resource = new AdminResource(gameWorker, bulkDataService, accountDbHandler, deckDbHandler, gameHistoryService);
    }

    @Test
    void shouldGetDraftStats() {
        Response response = resource.getDraftStats("pyramid");

        assertEquals(200, response.getStatus());
        assertEquals(50, response.getEntity());
    }

    @Test
    void shouldGetDonationSummary() {
        Response response = resource.getDonationSummary();

        assertEquals(200, response.getStatus());
        var stats = (DonationStatsResponse) response.getEntity();
        assertEquals(50, stats.totalDonated());
        assertEquals(40, stats.currentMonthly());
    }

    @Test
    void shouldGetAdminStatus() {
        Response response = resource.getAdminStatus();

        assertEquals(200, response.getStatus());
        var check = (AdminCheckResponse) response.getEntity();
        assertTrue(check.isAdmin());
    }

    @Test
    void shouldGetAccountByIDWhenFound() {
        var account = new Account();
        account.setAccountID("acc-1");
        account.setDisplayName("Alice");

        when(accountDbHandler.findById("acc-1")).thenReturn(Optional.of(account));

        Account result = resource.getAccountByID("acc-1").await().indefinitely();

        assertEquals("acc-1", result.getAccountID());
        assertEquals("Alice", result.getDisplayName());
    }

    @Test
    void shouldReturn404ForMissingAccountLookup() {
        when(accountDbHandler.findById("missing")).thenReturn(Optional.empty());

        assertThrows(WebApplicationException.class,
                () -> resource.getAccountByID("missing").await().indefinitely());
    }

    @Test
    void shouldGetGameHistoryByID() {
        when(gameHistoryService.getGameHistoryByAccountID("acc-1")).thenReturn(List.of());

        List<org.magic.accountService.api.GameHistoryEntry> result =
                resource.getGameHistoryByID("acc-1").await().indefinitely();

        assertNotNull(result);
    }

    @Test
    void shouldGetDecksByID() {
        var deck = new Deck();
        deck.setDeckID("deck-1");
        deck.setAccountID("acc-1");

        when(deckDbHandler.findByAccountID("acc-1")).thenReturn(List.of(deck));

        List<Deck> result = resource.getDecksByID("acc-1").await().indefinitely();

        assertEquals(1, result.size());
        assertEquals("deck-1", result.get(0).getDeckID());
    }

    @Test
    void shouldDeleteDeckByIDWhenOwned() {
        var deck = new Deck();
        deck.setDeckID("deck-1");
        deck.setAccountID("acc-1");

        when(deckDbHandler.findById("deck-1")).thenReturn(Optional.of(deck));

        Response response = resource.deleteDeckByID("acc-1", "deck-1").await().indefinitely();

        assertEquals(204, response.getStatus());
        verify(deckDbHandler).deleteDeck("deck-1");
        verify(accountDbHandler).removeDeckFromAccount("acc-1", "deck-1");
    }

    @Test
    void shouldReturn404WhenDeletingMissingDeck() {
        when(deckDbHandler.findById("deck-1")).thenReturn(Optional.empty());

        Response response = resource.deleteDeckByID("acc-1", "deck-1").await().indefinitely();

        assertEquals(404, response.getStatus());
        verify(deckDbHandler, never()).deleteDeck(any());
    }

    @Test
    void shouldReturn404WhenDeletingDeckOfAnotherAccount() {
        var deck = new Deck();
        deck.setDeckID("deck-1");
        deck.setAccountID("acc-other");

        when(deckDbHandler.findById("deck-1")).thenReturn(Optional.of(deck));

        Response response = resource.deleteDeckByID("acc-1", "deck-1").await().indefinitely();

        assertEquals(404, response.getStatus());
        verify(deckDbHandler, never()).deleteDeck(any());
    }

    @Test
    void shouldDeleteGamesWithStatus() {
        when(gameWorker.deleteGamesWithStatus(GameState.GAME_COMPLETE)).thenReturn(Uni.createFrom().item(Response.ok(5).build()));

        Response response = resource.deleteGamesWithStatus("game_complete").await().indefinitely();

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldReturn400ForInvalidGameState() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.deleteGamesWithStatus("not-a-state").await().indefinitely());

        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void shouldGetCacheStatus() {
        when(bulkDataService.getStatus()).thenReturn(
                new ScryfallBulkDataService.CacheStatus(true, 50000, "2026-07-29T21:10:03.341+00:00", "default_cards"));
        when(bulkDataService.isEnabled()).thenReturn(true);

        Response response = resource.getCacheStatus();

        assertEquals(200, response.getStatus());
        var status = (ScryfallCacheStatusResponse) response.getEntity();
        assertTrue(status.available());
        assertEquals(50000, status.cardCount());
    }

    @Test
    void shouldTriggerCacheRefresh() throws Exception {
        when(bulkDataService.triggerRefresh()).thenReturn(50000);
        when(bulkDataService.getStatus()).thenReturn(
                new ScryfallBulkDataService.CacheStatus(true, 50000, "2026-07-29T21:10:03.341+00:00", "default_cards"));
        when(bulkDataService.isEnabled()).thenReturn(true);

        Response response = resource.triggerCacheRefresh();

        assertEquals(200, response.getStatus());
        var status = (ScryfallCacheStatusResponse) response.getEntity();
        assertTrue(status.available());
    }

    @Test
    void shouldHandleCacheRefreshFailure() throws Exception {
        when(bulkDataService.triggerRefresh()).thenThrow(new java.io.IOException("Network error"));

        Response response = resource.triggerCacheRefresh();

        assertEquals(500, response.getStatus());
    }
}
