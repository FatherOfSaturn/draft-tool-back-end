package org.magic.accountService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Account;
import org.magic.accountService.api.CreateDeckRequest;
import org.magic.accountService.api.Deck;
import org.magic.accountService.api.LoginRequest;
import org.magic.accountService.api.UpdateDeckRequest;
import org.magic.accountService.api.UpdateDisplayNameRequest;
import org.magic.accountService.app.AccountWorker;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class AccountResourceTest {

    @Mock
    AccountWorker accountService;

    AccountResource resource;

    @BeforeEach
    void setup() {
        resource = new AccountResource(accountService);
    }

    @Test
    void shouldLoginWithValidToken() {
        var account = new Account();
        account.setAccountID("acc-123");
        account.setEmail("test@example.com");

        when(accountService.login("valid-token")).thenReturn(Uni.createFrom().item(account));

        Account result = resource.login(new LoginRequest("valid-token")).await().indefinitely();

        assertNotNull(result);
        assertEquals("acc-123", result.getAccountID());
    }

    @Test
    void shouldThrowUnauthorizedForInvalidToken() {
        when(accountService.login("bad-token")).thenReturn(Uni.createFrom().item((Account) null));

        assertThrows(WebApplicationException.class,
                () -> resource.login(new LoginRequest("bad-token")).await().indefinitely());
    }

    @Test
    void shouldGetAccountWhenFound() {
        var account = new Account();
        account.setAccountID("acc-123");

        when(accountService.getAccount("acc-123")).thenReturn(Uni.createFrom().item(Optional.of(account)));

        Account result = resource.getAccount("acc-123").await().indefinitely();

        assertEquals("acc-123", result.getAccountID());
    }

    @Test
    void shouldThrowNotFoundForMissingAccount() {
        when(accountService.getAccount("missing")).thenReturn(Uni.createFrom().item(Optional.empty()));

        assertThrows(WebApplicationException.class,
                () -> resource.getAccount("missing").await().indefinitely());
    }

    @Test
    void shouldUpdateDisplayNameWhenFound() {
        var request = new UpdateDisplayNameRequest("New Name");

        when(accountService.updateDisplayName("acc-123", request)).thenReturn(Uni.createFrom().item(true));

        Response response = resource.updateDisplayName("acc-123", request).await().indefinitely();

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldReturnNotFoundForUpdateDisplayNameOnMissingAccount() {
        var request = new UpdateDisplayNameRequest("New Name");

        when(accountService.updateDisplayName("missing", request)).thenReturn(Uni.createFrom().item(false));

        Response response = resource.updateDisplayName("missing", request).await().indefinitely();

        assertEquals(404, response.getStatus());
    }

    @Test
    void shouldGetDecks() {
        var decks = List.of(new Deck());

        when(accountService.getDecks("acc-123")).thenReturn(Uni.createFrom().item(decks));

        List<Deck> result = resource.getDecks("acc-123").await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void shouldCreateDeckWhenAccountFound() {
        var request = new CreateDeckRequest("My Deck", "Description", List.of("card1"));

        when(accountService.createDeck("acc-123", request)).thenReturn(Uni.createFrom().item(Optional.of("deck-456")));

        Response response = resource.createDeck("acc-123", request).await().indefinitely();

        assertEquals(201, response.getStatus());
        assertEquals("/account/acc-123/decks/deck-456", response.getLocation().toString());
    }

    @Test
    void shouldReturnNotFoundForCreateDeckOnMissingAccount() {
        var request = new CreateDeckRequest("My Deck", "Description", List.of("card1"));

        when(accountService.createDeck("missing", request)).thenReturn(Uni.createFrom().item(Optional.empty()));

        Response response = resource.createDeck("missing", request).await().indefinitely();

        assertEquals(404, response.getStatus());
    }

    @Test
    void shouldGetDeckWhenFound() {
        var deck = new Deck();
        deck.setDeckID("deck-456");

        when(accountService.getDeck("deck-456")).thenReturn(Uni.createFrom().item(Optional.of(deck)));

        Deck result = resource.getDeck("acc-123", "deck-456").await().indefinitely();

        assertEquals("deck-456", result.getDeckID());
    }

    @Test
    void shouldThrowNotFoundForMissingDeck() {
        when(accountService.getDeck("missing")).thenReturn(Uni.createFrom().item(Optional.empty()));

        assertThrows(WebApplicationException.class,
                () -> resource.getDeck("acc-123", "missing").await().indefinitely());
    }

    @Test
    void shouldUpdateDeckWhenFound() {
        var request = new UpdateDeckRequest("New Name", "New Desc", List.of("card1", "card2"));

        when(accountService.updateDeck("deck-456", request)).thenReturn(Uni.createFrom().item(true));

        Response response = resource.updateDeck("acc-123", "deck-456", request).await().indefinitely();

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldReturnNotFoundForUpdateDeckOnMissingDeck() {
        var request = new UpdateDeckRequest("New Name", "New Desc", List.of("card1"));

        when(accountService.updateDeck("missing", request)).thenReturn(Uni.createFrom().item(false));

        Response response = resource.updateDeck("acc-123", "missing", request).await().indefinitely();

        assertEquals(404, response.getStatus());
    }

    @Test
    void shouldDeleteDeckWhenFound() {
        when(accountService.deleteDeck("deck-456")).thenReturn(Uni.createFrom().item(true));

        Response response = resource.deleteDeck("acc-123", "deck-456").await().indefinitely();

        assertEquals(204, response.getStatus());
    }

    @Test
    void shouldReturnNotFoundForDeleteDeckOnMissingDeck() {
        when(accountService.deleteDeck("missing")).thenReturn(Uni.createFrom().item(false));

        Response response = resource.deleteDeck("acc-123", "missing").await().indefinitely();

        assertEquals(404, response.getStatus());
    }
}
