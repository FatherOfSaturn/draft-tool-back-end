package org.magic.accountService.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Deck;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class DeckDbHandlerTest {

    @Mock
    ReactiveMongoCollection<Deck> collection;

    DeckDbHandler handler;

    @BeforeEach
    void setup() {
        handler = spy(new DeckDbHandler());
        doReturn(collection).when(handler).mongoCollection();
    }

    private static Uni<InsertOneResult> inserted() {
        return Uni.createFrom().item(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId())));
    }

    @Test
    void shouldFindByAccountID() {
        when(collection.find(any(Document.class)))
                .thenReturn(Multi.createFrom().items(new Deck(), new Deck()));

        List<Deck> result = handler.findByAccountID("acc-1").await().indefinitely();

        assertEquals(2, result.size());
    }

    @Test
    void shouldFindById() {
        var deck = new Deck();
        deck.setDeckID("deck-1");

        when(collection.find(any(Document.class))).thenReturn(Multi.createFrom().item(deck));

        Optional<Deck> result = handler.findById("deck-1").await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals("deck-1", result.get().getDeckID());
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        when(collection.find(any(Document.class))).thenReturn(Multi.createFrom().empty());

        assertTrue(handler.findById("unknown").await().indefinitely().isEmpty());
    }

    @Test
    void shouldCreateDeck() {
        when(collection.insertOne(any(Deck.class))).thenReturn(inserted());

        String result = handler.createDeck("acc-1", "My Deck", "A description",
                List.of("card-1", "card-2")).await().indefinitely();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        verify(collection).insertOne(argThat(deck ->
                "acc-1".equals(deck.getAccountID()) &&
                "My Deck".equals(deck.getName()) &&
                "A description".equals(deck.getDescription()) &&
                deck.getCardIds().size() == 2));
    }

    @Test
    void shouldUpdateDeck() {
        when(collection.updateOne(any(Document.class), any(Document.class)))
                .thenReturn(Uni.createFrom().item(UpdateResult.acknowledged(1L, 1L, null)));

        handler.updateDeck("deck-1", "New Name", "New Desc", List.of("card-1")).await().indefinitely();

        verify(collection).updateOne(any(Document.class), any(Document.class));
    }

    @Test
    void shouldDeleteDeck() {
        when(collection.deleteOne(any(Document.class)))
                .thenReturn(Uni.createFrom().item(DeleteResult.acknowledged(1)));

        handler.deleteDeck("deck-1").await().indefinitely();

        verify(collection).deleteOne(any(Document.class));
    }
}
