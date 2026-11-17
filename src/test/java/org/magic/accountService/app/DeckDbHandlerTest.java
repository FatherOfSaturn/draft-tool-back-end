package org.magic.accountService.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Deck;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.result.UpdateResult;

import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class DeckDbHandlerTest {

    @Mock
    ReactiveMongoCollection<Deck> collection;

    DeckDbHandler handler;

    @BeforeEach
    void setup() {
        handler = spy(new DeckDbHandler());
        lenient().doReturn(collection).when(handler).mongoCollection();
    }

    @Test
    void shouldFindByAccountID() {
        doReturn(Uni.createFrom().item(List.of(new Deck(), new Deck())))
                .when(handler).list("accountID = ?1", "acc-1");

        List<Deck> result = handler.findByAccountID("acc-1").await().indefinitely();

        assertEquals(2, result.size());
    }

    @Test
    void shouldFindById() {
        var deck = new Deck();
        deck.setDeckID("deck-1");
        doReturn(Uni.createFrom().item(Optional.of(deck)))
                .when(handler).findByIdOptional("deck-1");

        Optional<Deck> result = handler.findByIdOptional("deck-1").await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals("deck-1", result.get().getDeckID());
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        doReturn(Uni.createFrom().item(Optional.empty()))
                .when(handler).findByIdOptional("unknown");

        assertTrue(handler.findByIdOptional("unknown").await().indefinitely().isEmpty());
    }

    @Test
    void shouldCreateDeck() {
        doReturn(Uni.createFrom().item((Deck) null)).when(handler).persist(any(Deck.class));

        String result = handler.createDeck("acc-1", "My Deck", "A description",
                List.of("card-1", "card-2")).await().indefinitely();

        assertEquals(24, result.length(), "ObjectId hex string");
        verify(handler).persist((Deck) argThat(deck ->
                "acc-1".equals(((Deck) deck).getAccountID()) &&
                "My Deck".equals(((Deck) deck).getName()) &&
                "A description".equals(((Deck) deck).getDescription()) &&
                ((Deck) deck).getCardIds().size() == 2));
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
        doReturn(Uni.createFrom().item(true)).when(handler).deleteById("deck-1");

        handler.deleteDeck("deck-1").await().indefinitely();

        verify(handler).deleteById("deck-1");
    }
}
