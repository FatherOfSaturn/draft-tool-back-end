package org.magic.accountService.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Account;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class AccountDbHandlerTest {

    @Mock
    ReactiveMongoCollection<Account> collection;

    AccountDbHandler handler;

    @BeforeEach
    void setup() {
        handler = spy(new AccountDbHandler());
        doReturn(collection).when(handler).mongoCollection();
    }

    private static Uni<InsertOneResult> inserted() {
        return Uni.createFrom().item(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId())));
    }

    @Test
    void shouldFindByGoogleSub() {
        var account = new Account();
        account.setAccountID("acc-1");
        account.setGoogleSub("sub-123");

        when(collection.find(any(Document.class))).thenReturn(Multi.createFrom().item(account));

        Optional<Account> result = handler.findByGoogleSub("sub-123").await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals("sub-123", result.get().getGoogleSub());
    }

    @Test
    void shouldReturnEmptyForUnknownGoogleSub() {
        when(collection.find(any(Document.class))).thenReturn(Multi.createFrom().empty());

        assertTrue(handler.findByGoogleSub("unknown").await().indefinitely().isEmpty());
    }

    @Test
    void shouldFindById() {
        var account = new Account();
        account.setAccountID("acc-1");

        when(collection.find(any(Document.class))).thenReturn(Multi.createFrom().item(account));

        Optional<Account> result = handler.findById("acc-1").await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals("acc-1", result.get().getAccountID());
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        when(collection.find(any(Document.class))).thenReturn(Multi.createFrom().empty());

        assertTrue(handler.findById("unknown").await().indefinitely().isEmpty());
    }

    @Test
    void shouldCreateAccount() {
        when(collection.insertOne(any(Account.class))).thenReturn(inserted());

        Account result = handler.createAccount("sub-123", "test@example.com", "Test User")
                .await().indefinitely();

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
        when(collection.updateOne(any(Document.class), any(Document.class)))
                .thenReturn(Uni.createFrom().item(UpdateResult.acknowledged(1L, 1L, null)));

        handler.updateDisplayName("acc-1", "New Name").await().indefinitely();

        verify(collection).updateOne(any(Document.class), any(Document.class));
    }

    @Test
    void shouldAddDeckToAccount() {
        when(collection.updateOne(any(Document.class), any(Document.class)))
                .thenReturn(Uni.createFrom().item(UpdateResult.acknowledged(1L, 1L, null)));

        handler.addDeckToAccount("acc-1", "deck-1").await().indefinitely();

        verify(collection).updateOne(any(Document.class), any(Document.class));
    }

    @Test
    void shouldRemoveDeckFromAccount() {
        when(collection.updateOne(any(Document.class), any(Document.class)))
                .thenReturn(Uni.createFrom().item(UpdateResult.acknowledged(1L, 1L, null)));

        handler.removeDeckFromAccount("acc-1", "deck-1").await().indefinitely();

        verify(collection).updateOne(any(Document.class), any(Document.class));
    }

    @Test
    void newAccountIdShouldBeAnObjectIdHex() {
        when(collection.insertOne(any(Account.class))).thenReturn(inserted());

        Account result = handler.createAccount("sub", "e@x.com", "X").await().indefinitely();

        assertFalse(result.getAccountID().isBlank());
        assertEquals(24, result.getAccountID().length(), "ObjectId hex string");
    }
}
