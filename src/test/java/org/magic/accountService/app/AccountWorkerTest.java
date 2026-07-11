package org.magic.accountService.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Account;
import org.magic.accountService.api.CreateDeckRequest;
import org.magic.accountService.api.Deck;
import org.magic.accountService.api.UpdateDeckRequest;
import org.magic.accountService.api.UpdateDisplayNameRequest;
import org.magic.accountService.external.GoogleTokenVerifier;
import org.magic.accountService.external.GoogleTokenVerifier.GoogleUser;
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

    AccountWorker accountService;

    String testAccountID = "507f1f77bcf86cd799439011";

    @BeforeEach
    void setup() {
        accountService = new AccountWorker(tokenVerifier, accountDbHandler, deckDbHandler);
    }

    @Test
    void shouldCreateAccountOnFirstLogin() {
        String idToken = "test-token";
        GoogleUser googleUser = new GoogleUser("sub123", "test@example.com", "Test User");

        when(tokenVerifier.verify(idToken)).thenReturn(googleUser);
        when(accountDbHandler.findByGoogleSub("sub123")).thenReturn(Optional.empty());

        Account expectedAccount = new Account();
        expectedAccount.setAccountID(testAccountID);
        expectedAccount.setGoogleSub("sub123");
        expectedAccount.setEmail("test@example.com");
        expectedAccount.setDisplayName("Test User");
        expectedAccount.setDeckIDs(List.of());

        when(accountDbHandler.createAccount("sub123", "test@example.com", "Test User")).thenReturn(expectedAccount);

        Account result = accountService.login(idToken).await().indefinitely();

        assertNotNull(result);
        assertEquals("sub123", result.getGoogleSub());
        verify(accountDbHandler).createAccount("sub123", "test@example.com", "Test User");
    }

    @Test
    void shouldReturnExistingAccountOnSubsequentLogin() {
        String idToken = "test-token";
        GoogleUser googleUser = new GoogleUser("sub123", "test@example.com", "Test User");

        when(tokenVerifier.verify(idToken)).thenReturn(googleUser);

        Account existing = new Account();
        existing.setAccountID(testAccountID);
        existing.setGoogleSub("sub123");
        existing.setEmail("test@example.com");
        existing.setDisplayName("Test User");

        when(accountDbHandler.findByGoogleSub("sub123")).thenReturn(Optional.of(existing));

        Account result = accountService.login(idToken).await().indefinitely();

        assertNotNull(result);
        assertEquals("sub123", result.getGoogleSub());
    }

    @Test
    void shouldReturnNullForInvalidToken() {
        when(tokenVerifier.verify("bad-token")).thenReturn(null);

        Account result = accountService.login("bad-token").await().indefinitely();

        assertEquals(null, result);
    }

    @Test
    void shouldGetAccount() {
        Account account = new Account();
        account.setAccountID(testAccountID);

        when(accountDbHandler.findById(testAccountID)).thenReturn(Optional.of(account));

        Optional<Account> result = accountService.getAccount(testAccountID).await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals(testAccountID, result.get().getAccountID());
    }

    @Test
    void shouldReturnEmptyForMissingAccount() {
        String missingID = "507f1f77bcf86cd799439099";
        when(accountDbHandler.findById(missingID)).thenReturn(Optional.empty());

        Optional<Account> result = accountService.getAccount(missingID).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUpdateDisplayName() {
        UpdateDisplayNameRequest request = new UpdateDisplayNameRequest("New Name");

        when(accountDbHandler.findById(testAccountID)).thenReturn(Optional.of(new Account()));

        boolean result = accountService.updateDisplayName(testAccountID, request).await().indefinitely();

        assertTrue(result);
        verify(accountDbHandler).updateDisplayName(testAccountID, "New Name");
    }

    @Test
    void shouldReturnFalseForUpdateDisplayNameOnMissingAccount() {
        String missingID = "507f1f77bcf86cd799439099";
        when(accountDbHandler.findById(missingID)).thenReturn(Optional.empty());

        boolean result = accountService.updateDisplayName(missingID, new UpdateDisplayNameRequest("Name")).await().indefinitely();

        assertFalse(result);
    }

    @Test
    void shouldCreateDeck() {
        String deckID = "507f1f77bcf86cd799439012";
        CreateDeckRequest request = new CreateDeckRequest("My Deck", "Description", List.of("card1"));

        when(accountDbHandler.findById(testAccountID)).thenReturn(Optional.of(new Account()));
        when(deckDbHandler.createDeck(any(), any(), any(), any())).thenReturn(deckID);

        Optional<String> result = accountService.createDeck(testAccountID, request).await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals(deckID, result.get());
        verify(accountDbHandler).addDeckToAccount(testAccountID, deckID);
    }

    @Test
    void shouldNotCreateDeckForMissingAccount() {
        String missingID = "507f1f77bcf86cd799439099";
        when(accountDbHandler.findById(missingID)).thenReturn(Optional.empty());

        Optional<String> result = accountService.createDeck(missingID, new CreateDeckRequest("Deck", "", List.of())).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldGetDecks() {
        List<Deck> decks = List.of(new Deck());

        when(deckDbHandler.findByAccountID(testAccountID)).thenReturn(decks);

        List<Deck> result = accountService.getDecks(testAccountID).await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void shouldUpdateDeck() {
        String deckID = "507f1f77bcf86cd799439012";
        UpdateDeckRequest request = new UpdateDeckRequest("New Name", "New Desc", List.of("card1", "card2"));

        when(deckDbHandler.findById(deckID)).thenReturn(Optional.of(new Deck()));

        boolean result = accountService.updateDeck(deckID, request).await().indefinitely();

        assertTrue(result);
        verify(deckDbHandler).updateDeck(deckID, "New Name", "New Desc", List.of("card1", "card2"));
    }

    @Test
    void shouldReturnFalseForUpdateDeckOnMissingDeck() {
        String missingID = "507f1f77bcf86cd799439099";
        when(deckDbHandler.findById(missingID)).thenReturn(Optional.empty());

        boolean result = accountService.updateDeck(missingID, new UpdateDeckRequest("", "", List.of())).await().indefinitely();

        assertFalse(result);
    }

    @Test
    void shouldDeleteDeck() {
        String deckID = "507f1f77bcf86cd799439012";
        Deck deck = new Deck();
        deck.setAccountID(testAccountID);

        when(deckDbHandler.findById(deckID)).thenReturn(Optional.of(deck));

        boolean result = accountService.deleteDeck(deckID).await().indefinitely();

        assertTrue(result);
        verify(deckDbHandler).deleteDeck(deckID);
        verify(accountDbHandler).removeDeckFromAccount(testAccountID, deckID);
    }

    @Test
    void shouldReturnFalseForDeleteDeckOnMissingDeck() {
        String missingID = "507f1f77bcf86cd799439099";
        when(deckDbHandler.findById(missingID)).thenReturn(Optional.empty());

        boolean result = accountService.deleteDeck(missingID).await().indefinitely();

        assertFalse(result);
    }
}
