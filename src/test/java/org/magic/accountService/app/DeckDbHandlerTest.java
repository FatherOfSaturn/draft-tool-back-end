package org.magic.accountService.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Deck;
import org.magic.pyramidDraft.app.GameCoordination.MongoService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

@ExtendWith(MockitoExtension.class)
class DeckDbHandlerTest {

    @Mock
    MongoService mongoService;
    @Mock
    MongoDatabase database;
    @Mock
    MongoCollection<Deck> collection;
    @Mock
    FindIterable<Deck> findIterable;

    DeckDbHandler handler;

    @BeforeEach
    void setup() {
        when(mongoService.getDatabase()).thenReturn(database);
        when(database.getCollection("Decks", Deck.class)).thenReturn(collection);
        handler = new DeckDbHandler(mongoService);
    }

    @Test
    void shouldFindByAccountID() {
        var decks = List.of(new Deck(), new Deck());

        when(collection.find(any(Document.class))).thenReturn(findIterable);
        when(findIterable.into(any())).thenAnswer(invocation -> {
            ArrayList<Deck> list = invocation.getArgument(0);
            list.addAll(decks);
            return list;
        });

        List<Deck> result = handler.findByAccountID("acc-1");

        assertEquals(2, result.size());
    }

    @Test
    void shouldFindById() {
        var deck = new Deck();
        deck.setDeckID("deck-1");

        when(collection.find(any(Document.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(deck);

        Optional<Deck> result = handler.findById("deck-1");

        assertTrue(result.isPresent());
        assertEquals("deck-1", result.get().getDeckID());
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        when(collection.find(any(Document.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(null);

        Optional<Deck> result = handler.findById("unknown");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldCreateDeck() {
        String result = handler.createDeck("acc-1", "My Deck", "A description", List.of("card-1", "card-2"));

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
        handler.updateDeck("deck-1", "New Name", "New Desc", List.of("card-1"));

        verify(collection).updateOne(any(Document.class), any(Document.class));
    }

    @Test
    void shouldDeleteDeck() {
        handler.deleteDeck("deck-1");

        verify(collection).deleteOne(any(Document.class));
    }
}
