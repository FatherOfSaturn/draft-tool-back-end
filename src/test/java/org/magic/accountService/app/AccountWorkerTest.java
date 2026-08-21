package org.magic.accountService.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Account;
import org.magic.accountService.api.CreateDeckRequest;
import org.magic.accountService.api.Deck;
import org.magic.accountService.api.LoginResponse;
import org.magic.accountService.api.UpdateDeckRequest;
import org.magic.accountService.api.UpdateDisplayNameRequest;
import org.magic.accountService.external.GoogleTokenVerifier;
import org.magic.accountService.external.GoogleTokenVerifier.GoogleUser;
import org.magic.common.admin.AdminService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;



@ExtendWith(MockitoExtension.class)
class AccountWorkerTest {

    @Mock
    GoogleTokenVerifier tokenVerifier;

    @Mock
    AccountDbHandler accountDbHandler;

    @Mock
    DeckDbHandler deckDbHandler;

    @Mock
    JwtService jwtService;

    @Mock
    AdminService adminService;

    AccountWorker accountService;

    String testAccountID = "507f1f77bcf86cd799439011";
    String googleSub = "sub123";

    @BeforeEach
    void setup() {
        accountService = new AccountWorker(tokenVerifier, accountDbHandler, deckDbHandler, jwtService, adminService);
    }

    @Test
    void shouldCreateAccountOnFirstLogin() {
        String idToken = "test-token";
        GoogleUser googleUser = new GoogleUser(googleSub, "test@example.com", "Test User");

        when(tokenVerifier.verify(idToken)).thenReturn(googleUser);
        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.empty());

        Account expectedAccount = new Account();
        expectedAccount.setAccountID(testAccountID);
        expectedAccount.setGoogleSub(googleSub);
        expectedAccount.setEmail("test@example.com");
        expectedAccount.setDisplayName("Test User");
        expectedAccount.setDeckIDs(List.of());

        when(accountDbHandler.createAccount(googleSub, "test@example.com", "Test User")).thenReturn(expectedAccount);
        when(adminService.isAdmin("test@example.com")).thenReturn(false);
        when(jwtService.issueToken(any(), any(), any())).thenReturn("jwt-token");
        when(jwtService.getTokenTtlSeconds()).thenReturn(86400L);

        LoginResponse result = accountService.login(idToken).await().indefinitely();

        assertNotNull(result);
        assertEquals(googleSub, result.account().getGoogleSub());
        assertEquals("jwt-token", result.jwt());
        assertEquals(86400L, result.expiresIn());
        verify(accountDbHandler).createAccount(googleSub, "test@example.com", "Test User");
    }

    @Test
    void shouldReturnExistingAccountOnSubsequentLogin() {
        String idToken = "test-token";
        GoogleUser googleUser = new GoogleUser(googleSub, "test@example.com", "Test User");

        when(tokenVerifier.verify(idToken)).thenReturn(googleUser);

        Account existing = new Account();
        existing.setAccountID(testAccountID);
        existing.setGoogleSub(googleSub);
        existing.setEmail("test@example.com");
        existing.setDisplayName("Test User");

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(existing));
        when(adminService.isAdmin("test@example.com")).thenReturn(false);
        when(jwtService.issueToken(any(), any(), any())).thenReturn("jwt-token");
        when(jwtService.getTokenTtlSeconds()).thenReturn(86400L);

        LoginResponse result = accountService.login(idToken).await().indefinitely();

        assertNotNull(result);
        assertEquals(googleSub, result.account().getGoogleSub());
        verify(accountDbHandler, never()).createAccount(any(), any(), any());
    }

    @Test
    void shouldReturnNullForInvalidToken() {
        when(tokenVerifier.verify("bad-token")).thenReturn(null);

        LoginResponse result = accountService.login("bad-token").await().indefinitely();

        assertNull(result);
    }

    @Test
    void shouldIssueAdminGroupForAdminEmail() {
        String idToken = "test-token";
        GoogleUser googleUser = new GoogleUser(googleSub, "admin@example.com", "Admin User");

        when(tokenVerifier.verify(idToken)).thenReturn(googleUser);

        Account existing = new Account();
        existing.setAccountID(testAccountID);
        existing.setGoogleSub(googleSub);
        existing.setEmail("admin@example.com");

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(existing));
        when(adminService.isAdmin("admin@example.com")).thenReturn(true);
        when(jwtService.getTokenTtlSeconds()).thenReturn(86400L);

        accountService.login(idToken).await().indefinitely();

        ArgumentCaptor<Set<String>> groupsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(jwtService).issueToken(eq(googleSub), eq("admin@example.com"), groupsCaptor.capture());
        assertTrue(groupsCaptor.getValue().contains("user"));
        assertTrue(groupsCaptor.getValue().contains("admin"));
    }

    @Test
    void shouldGetAccount() {
        Account account = new Account();
        account.setAccountID(testAccountID);

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));

        Optional<Account> result = accountService.getAccount(googleSub).await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals(testAccountID, result.get().getAccountID());
    }

    @Test
    void shouldReturnEmptyForMissingAccount() {
        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.empty());

        Optional<Account> result = accountService.getAccount(googleSub).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUpdateDisplayName() {
        UpdateDisplayNameRequest request = new UpdateDisplayNameRequest("New Name");
        Account account = new Account();
        account.setAccountID(testAccountID);

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));

        boolean result = accountService.updateDisplayName(googleSub, request).await().indefinitely();

        assertTrue(result);
        verify(accountDbHandler).updateDisplayName(testAccountID, "New Name");
    }

    @Test
    void shouldReturnFalseForUpdateDisplayNameOnMissingAccount() {
        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.empty());

        boolean result = accountService.updateDisplayName(googleSub, new UpdateDisplayNameRequest("Name")).await().indefinitely();

        assertFalse(result);
    }

    @Test
    void shouldCreateDeck() {
        String deckID = "507f1f77bcf86cd799439012";
        CreateDeckRequest request = new CreateDeckRequest("My Deck", "Description", List.of("card1"));
        Account account = new Account();
        account.setAccountID(testAccountID);

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));
        when(deckDbHandler.createDeck(any(), any(), any(), any())).thenReturn(deckID);

        Optional<String> result = accountService.createDeck(googleSub, request).await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals(deckID, result.get());
        verify(accountDbHandler).addDeckToAccount(testAccountID, deckID);
    }

    @Test
    void shouldNotCreateDeckForMissingAccount() {
        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.empty());

        Optional<String> result = accountService.createDeck(googleSub, new CreateDeckRequest("Deck", "", List.of())).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldGetDecks() {
        List<Deck> decks = List.of(new Deck());
        Account account = new Account();
        account.setAccountID(testAccountID);

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));
        when(deckDbHandler.findByAccountID(testAccountID)).thenReturn(decks);

        List<Deck> result = accountService.getDecks(googleSub).await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetEmptyDecksForMissingAccount() {
        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.empty());

        List<Deck> result = accountService.getDecks(googleSub).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldGetDeck() {
        String deckID = "507f1f77bcf86cd799439012";
        Account account = new Account();
        account.setAccountID(testAccountID);
        Deck deck = new Deck();
        deck.setDeckID(deckID);
        deck.setAccountID(testAccountID);

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));
        when(deckDbHandler.findById(deckID)).thenReturn(Optional.of(deck));

        Optional<Deck> result = accountService.getDeck(googleSub, deckID).await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals(deckID, result.get().getDeckID());
    }

    @Test
    void shouldNotGetDeckOwnedByAnotherUser() {
        String deckID = "507f1f77bcf86cd799439012";
        Account account = new Account();
        account.setAccountID(testAccountID);
        Deck deck = new Deck();
        deck.setDeckID(deckID);
        deck.setAccountID("someone-else");

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));
        when(deckDbHandler.findById(deckID)).thenReturn(Optional.of(deck));

        Optional<Deck> result = accountService.getDeck(googleSub, deckID).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUpdateDeck() {
        String deckID = "507f1f77bcf86cd799439012";
        UpdateDeckRequest request = new UpdateDeckRequest("New Name", "New Desc", List.of("card1", "card2"));
        Account account = new Account();
        account.setAccountID(testAccountID);
        Deck deck = new Deck();
        deck.setAccountID(testAccountID);

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));
        when(deckDbHandler.findById(deckID)).thenReturn(Optional.of(deck));

        boolean result = accountService.updateDeck(googleSub, deckID, request).await().indefinitely();

        assertTrue(result);
        verify(deckDbHandler).updateDeck(deckID, "New Name", "New Desc", List.of("card1", "card2"));
    }

    @Test
    void shouldNotUpdateDeckOwnedByAnotherUser() {
        String deckID = "507f1f77bcf86cd799439012";
        UpdateDeckRequest request = new UpdateDeckRequest("New Name", "New Desc", List.of("card1"));
        Account account = new Account();
        account.setAccountID(testAccountID);
        Deck deck = new Deck();
        deck.setAccountID("someone-else");

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));
        when(deckDbHandler.findById(deckID)).thenReturn(Optional.of(deck));

        boolean result = accountService.updateDeck(googleSub, deckID, request).await().indefinitely();

        assertFalse(result);
        verify(deckDbHandler, never()).updateDeck(any(), any(), any(), any());
    }

    @Test
    void shouldReturnFalseForUpdateDeckOnMissingDeck() {
        String missingID = "507f1f77bcf86cd799439099";
        Account account = new Account();
        account.setAccountID(testAccountID);

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));
        when(deckDbHandler.findById(missingID)).thenReturn(Optional.empty());

        boolean result = accountService.updateDeck(googleSub, missingID, new UpdateDeckRequest("", "", List.of())).await().indefinitely();

        assertFalse(result);
    }

    @Test
    void shouldDeleteDeck() {
        String deckID = "507f1f77bcf86cd799439012";
        Account account = new Account();
        account.setAccountID(testAccountID);
        Deck deck = new Deck();
        deck.setDeckID(deckID);
        deck.setAccountID(testAccountID);

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));
        when(deckDbHandler.findById(deckID)).thenReturn(Optional.of(deck));

        boolean result = accountService.deleteDeck(googleSub, deckID).await().indefinitely();

        assertTrue(result);
        verify(deckDbHandler).deleteDeck(deckID);
        verify(accountDbHandler).removeDeckFromAccount(testAccountID, deckID);
    }

    @Test
    void shouldNotDeleteDeckOwnedByAnotherUser() {
        String deckID = "507f1f77bcf86cd799439012";
        Account account = new Account();
        account.setAccountID(testAccountID);
        Deck deck = new Deck();
        deck.setDeckID(deckID);
        deck.setAccountID("someone-else");

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));
        when(deckDbHandler.findById(deckID)).thenReturn(Optional.of(deck));

        boolean result = accountService.deleteDeck(googleSub, deckID).await().indefinitely();

        assertFalse(result);
        verify(deckDbHandler, never()).deleteDeck(any());
    }

    @Test
    void shouldReturnFalseForDeleteDeckOnMissingDeck() {
        String missingID = "507f1f77bcf86cd799439099";
        Account account = new Account();
        account.setAccountID(testAccountID);

        when(accountDbHandler.findByGoogleSub(googleSub)).thenReturn(Optional.of(account));
        when(deckDbHandler.findById(missingID)).thenReturn(Optional.empty());

        boolean result = accountService.deleteDeck(googleSub, missingID).await().indefinitely();

        assertFalse(result);
    }
}
