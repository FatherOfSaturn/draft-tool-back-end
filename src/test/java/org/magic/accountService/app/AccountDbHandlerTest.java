package org.magic.accountService.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Account;
import org.magic.pyramidDraft.app.GameCoordination.MongoService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;

@ExtendWith(MockitoExtension.class)
class AccountDbHandlerTest {

    @Mock
    MongoService mongoService;
    @Mock
    MongoDatabase database;
    @Mock
    MongoCollection<Account> collection;
    @Mock
    FindIterable<Account> findIterable;
    @Mock
    UpdateResult updateResult;

    AccountDbHandler handler;

    @BeforeEach
    void setup() {
        when(mongoService.getDatabase()).thenReturn(database);
        when(database.getCollection("Accounts", Account.class)).thenReturn(collection);
        handler = new AccountDbHandler(mongoService);
    }

    @Test
    void shouldFindByGoogleSub() {
        var account = new Account();
        account.setAccountID("acc-1");
        account.setGoogleSub("sub-123");

        when(collection.find(any(Document.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(account);

        Optional<Account> result = handler.findByGoogleSub("sub-123");

        assertTrue(result.isPresent());
        assertEquals("sub-123", result.get().getGoogleSub());
    }

    @Test
    void shouldReturnEmptyForUnknownGoogleSub() {
        when(collection.find(any(Document.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(null);

        Optional<Account> result = handler.findByGoogleSub("unknown");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindById() {
        var account = new Account();
        account.setAccountID("acc-1");

        when(collection.find(any(Document.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(account);

        Optional<Account> result = handler.findById("acc-1");

        assertTrue(result.isPresent());
        assertEquals("acc-1", result.get().getAccountID());
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        when(collection.find(any(Document.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(null);

        Optional<Account> result = handler.findById("unknown");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldCreateAccount() {
        var result = handler.createAccount("sub-123", "test@example.com", "Test User");

        assertNotNull(result);
        assertEquals("sub-123", result.getGoogleSub());
        assertEquals("test@example.com", result.getEmail());
        assertEquals("Test User", result.getDisplayName());
        assertNotNull(result.getAccountID());
        assertNotNull(result.getCreatedAt());
        assertTrue(result.getDeckIDs().isEmpty());
        verify(collection).insertOne(any(Account.class));
    }

    @Test
    void shouldUpdateDisplayName() {
        handler.updateDisplayName("acc-1", "New Name");

        verify(collection).updateOne(any(Document.class), any(Document.class));
    }

    @Test
    void shouldAddDeckToAccount() {
        handler.addDeckToAccount("acc-1", "deck-1");

        verify(collection).updateOne(any(Document.class), any(Document.class));
    }

    @Test
    void shouldRemoveDeckFromAccount() {
        handler.removeDeckFromAccount("acc-1", "deck-1");

        verify(collection).updateOne(any(Document.class), any(Document.class));
    }
}
