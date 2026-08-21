package org.magic.accountService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Account;
import org.magic.accountService.api.CreateDeckRequest;
import org.magic.accountService.api.Deck;
import org.magic.accountService.api.LoginRequest;
import org.magic.accountService.api.LoginResponse;
import org.magic.accountService.api.UpdateDeckRequest;
import org.magic.accountService.api.UpdateDisplayNameRequest;
import org.magic.accountService.app.AccountWorker;
import org.magic.common.TokenRevocationService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@ExtendWith(MockitoExtension.class)
class AccountResourceTest {

    @Mock
    AccountWorker accountService;

    @Mock
    TokenRevocationService tokenRevocationService;

    @Mock
    SecurityContext securityContext;

    @Mock
    Principal principal;

    AccountResource resource;

    String googleSub = "sub123";

    @BeforeEach
    void setup() {
        resource = new AccountResource(accountService, tokenRevocationService);
        lenient().when(securityContext.getUserPrincipal()).thenReturn(principal);
        lenient().when(principal.getName()).thenReturn(googleSub);
    }

    @Test
    void shouldLoginWithValidToken() {
        var account = new Account();
        account.setAccountID("acc-123");
        account.setEmail("test@example.com");
        var loginResponse = new LoginResponse(account, "jwt-token", 86400L);

        when(accountService.login("valid-token")).thenReturn(Uni.createFrom().item(loginResponse));

        LoginResponse result = resource.login(new LoginRequest("valid-token")).await().indefinitely();

        assertNotNull(result);
        assertEquals("acc-123", result.account().getAccountID());
        assertEquals("jwt-token", result.jwt());
    }

    @Test
    void shouldThrowUnauthorizedForInvalidToken() {
        when(accountService.login("bad-token")).thenReturn(Uni.createFrom().item((LoginResponse) null));

        assertThrows(WebApplicationException.class,
                () -> resource.login(new LoginRequest("bad-token")).await().indefinitely());
    }

    @Test
    void shouldLogoutAndRevokeToken() {
        Response response = resource.logout("Bearer jwt-to-revoke");

        assertEquals(204, response.getStatus());
        verify(tokenRevocationService).revoke("jwt-to-revoke");
    }

    @Test
    void shouldRejectLogoutWithoutBearerToken() {
        assertThrows(WebApplicationException.class, () -> resource.logout(null));
        assertThrows(WebApplicationException.class, () -> resource.logout("Basic abc123"));
        verifyNoInteractions(tokenRevocationService);
    }

    @Test
    void shouldGetAccountWhenFound() {
        var account = new Account();
        account.setAccountID("acc-123");

        when(accountService.getAccount(googleSub)).thenReturn(Uni.createFrom().item(Optional.of(account)));

        Account result = resource.getAccount(securityContext).await().indefinitely();

        assertEquals("acc-123", result.getAccountID());
    }

    @Test
    void shouldThrowNotFoundForMissingAccount() {
        when(accountService.getAccount(googleSub)).thenReturn(Uni.createFrom().item(Optional.empty()));

        assertThrows(WebApplicationException.class,
                () -> resource.getAccount(securityContext).await().indefinitely());
    }

    @Test
    void shouldUpdateDisplayNameWhenFound() {
        var request = new UpdateDisplayNameRequest("New Name");

        when(accountService.updateDisplayName(googleSub, request)).thenReturn(Uni.createFrom().item(true));

        Response response = resource.updateDisplayName(request, securityContext).await().indefinitely();

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldReturnNotFoundForUpdateDisplayNameOnMissingAccount() {
        var request = new UpdateDisplayNameRequest("New Name");

        when(accountService.updateDisplayName(googleSub, request)).thenReturn(Uni.createFrom().item(false));

        Response response = resource.updateDisplayName(request, securityContext).await().indefinitely();

        assertEquals(404, response.getStatus());
    }

    @Test
    void shouldGetDecks() {
        var decks = List.of(new Deck());

        when(accountService.getDecks(googleSub)).thenReturn(Uni.createFrom().item(decks));

        List<Deck> result = resource.getDecks(securityContext).await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void shouldCreateDeckWhenAccountFound() {
        var request = new CreateDeckRequest("My Deck", "Description", List.of("card1"));

        when(accountService.createDeck(googleSub, request)).thenReturn(Uni.createFrom().item(Optional.of("deck-456")));

        Response response = resource.createDeck(request, securityContext).await().indefinitely();

        assertEquals(201, response.getStatus());
        assertEquals("/account/decks/deck-456", response.getLocation().toString());
    }

    @Test
    void shouldReturnNotFoundForCreateDeckOnMissingAccount() {
        var request = new CreateDeckRequest("My Deck", "Description", List.of("card1"));

        when(accountService.createDeck(googleSub, request)).thenReturn(Uni.createFrom().item(Optional.empty()));

        Response response = resource.createDeck(request, securityContext).await().indefinitely();

        assertEquals(404, response.getStatus());
    }

    @Test
    void shouldGetDeckWhenFound() {
        var deck = new Deck();
        deck.setDeckID("deck-456");

        when(accountService.getDeck(googleSub, "deck-456")).thenReturn(Uni.createFrom().item(Optional.of(deck)));

        Deck result = resource.getDeck("deck-456", securityContext).await().indefinitely();

        assertEquals("deck-456", result.getDeckID());
    }

    @Test
    void shouldThrowNotFoundForMissingDeck() {
        when(accountService.getDeck(googleSub, "missing")).thenReturn(Uni.createFrom().item(Optional.empty()));

        assertThrows(WebApplicationException.class,
                () -> resource.getDeck("missing", securityContext).await().indefinitely());
    }

    @Test
    void shouldUpdateDeckWhenFound() {
        var request = new UpdateDeckRequest("New Name", "New Desc", List.of("card1", "card2"));

        when(accountService.updateDeck(googleSub, "deck-456", request)).thenReturn(Uni.createFrom().item(true));

        Response response = resource.updateDeck("deck-456", request, securityContext).await().indefinitely();

        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldReturnNotFoundForUpdateDeckOnMissingDeck() {
        var request = new UpdateDeckRequest("New Name", "New Desc", List.of("card1"));

        when(accountService.updateDeck(googleSub, "missing", request)).thenReturn(Uni.createFrom().item(false));

        Response response = resource.updateDeck("missing", request, securityContext).await().indefinitely();

        assertEquals(404, response.getStatus());
    }

    @Test
    void shouldDeleteDeckWhenFound() {
        when(accountService.deleteDeck(googleSub, "deck-456")).thenReturn(Uni.createFrom().item(true));

        Response response = resource.deleteDeck("deck-456", securityContext).await().indefinitely();

        assertEquals(204, response.getStatus());
    }

    @Test
    void shouldReturnNotFoundForDeleteDeckOnMissingDeck() {
        when(accountService.deleteDeck(googleSub, "missing")).thenReturn(Uni.createFrom().item(false));

        Response response = resource.deleteDeck("missing", securityContext).await().indefinitely();

        assertEquals(404, response.getStatus());
    }
}
