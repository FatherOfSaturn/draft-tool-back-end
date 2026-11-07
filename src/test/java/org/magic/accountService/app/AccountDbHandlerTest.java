package org.magic.accountService.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Account;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.result.UpdateResult;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class AccountDbHandlerTest {

    @Mock
    ReactiveMongoCollection<Account> collection;

    @Mock
    ReactivePanacheQuery<Account> query;

    AccountDbHandler handler;

    @BeforeEach
    void setup() {
        handler = spy(new AccountDbHandler());
        lenient().doReturn(collection).when(handler).mongoCollection();
    }

    @Test
    void shouldFindByGoogleSub() {
        var account = new Account();
        account.setAccountID("acc-1");
        account.setGoogleSub("sub-123");

        doReturn(query).when(handler).find("googleSub = ?1", "sub-123");
        when(query.firstResultOptional()).thenReturn(Uni.createFrom().item(Optional.of(account)));

        Optional<Account> result = handler.findByGoogleSub("sub-123").await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals("sub-123", result.get().getGoogleSub());
    }

    @Test
    void shouldReturnEmptyForUnknownGoogleSub() {
        doReturn(query).when(handler).find("googleSub = ?1", "unknown");
        when(query.firstResultOptional()).thenReturn(Uni.createFrom().item(Optional.empty()));

        assertTrue(handler.findByGoogleSub("unknown").await().indefinitely().isEmpty());
    }

    @Test
    void shouldFindById() {
        var account = new Account();
        account.setAccountID("acc-1");

        doReturn(Uni.createFrom().item(Optional.of(account)))
                .when(handler).findByIdOptional("acc-1");

        Optional<Account> result = handler.findByIdOptional("acc-1").await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals("acc-1", result.get().getAccountID());
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        doReturn(Uni.createFrom().item(Optional.empty()))
                .when(handler).findByIdOptional("unknown");

        assertTrue(handler.findByIdOptional("unknown").await().indefinitely().isEmpty());
    }

    @Test
    void shouldCreateAccount() {
        doReturn(Uni.createFrom().item((Account) null))
                .when(handler).persist(any(Account.class));

        Account result = handler.createAccount("sub-123", "test@example.com", "Test User")
                .await().indefinitely();

        assertNotNull(result);
        assertEquals("sub-123", result.getGoogleSub());
        assertEquals("test@example.com", result.getEmail());
        assertEquals("Test User", result.getDisplayName());
        assertNotNull(result.getAccountID());
        assertNotNull(result.getCreatedAt());
        assertTrue(result.getDeckIDs().isEmpty());
        assertFalse(result.getAccountID().isBlank());
        assertEquals(24, result.getAccountID().length(), "ObjectId hex string");
        verify(handler).persist(any(Account.class));
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
}
