package org.magic.pyramidDraft.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardDetails;
import org.magic.pyramidDraft.api.card.CardPack;

class CardPackTest {

    private Card createCard(String cardID, String name) {
        CardDetails details = new CardDetails("set", "set_name", cardID,
            "https://example.com/small.jpg", "https://example.com/normal.jpg",
            null, name, "Creature", 1, List.of("1"));
        return new Card(name, details, cardID, 1, "Creature");
    }

    @Test
    void testRemoveCardFromPack() {
        Card card1 = createCard("card-1", "Alpha");
        Card card2 = createCard("card-2", "Beta");
        Card card3 = createCard("card-3", "Gamma");

        CardPack pack = new CardPack(0, List.of(card1, card2, card3), 3, false);

        assertEquals(3, pack.getCardsInPack().size());

        Card removed = pack.removeCardFromPack("card-2");

        assertNotNull(removed);
        assertEquals("Beta", removed.getName());
        assertEquals(2, pack.getCardsInPack().size());
        assertFalse(pack.getCardsInPack().stream().anyMatch(c -> c.getCardID().equals("card-2")));
    }

    @Test
    void testRemoveCardFirstElement() {
        Card card1 = createCard("card-1", "Alpha");
        Card card2 = createCard("card-2", "Beta");

        CardPack pack = new CardPack(0, List.of(card1, card2), 2, false);

        Card removed = pack.removeCardFromPack("card-1");
        assertEquals("Alpha", removed.getName());
        assertEquals(1, pack.getCardsInPack().size());
    }

    @Test
    void testRemoveCardLastElement() {
        Card card1 = createCard("card-1", "Alpha");
        Card card2 = createCard("card-2", "Beta");

        CardPack pack = new CardPack(0, List.of(card1, card2), 2, false);

        Card removed = pack.removeCardFromPack("card-2");
        assertEquals("Beta", removed.getName());
        assertEquals(1, pack.getCardsInPack().size());
    }

    @Test
    void testRemoveNonExistentCardThrows() {
        Card card1 = createCard("card-1", "Alpha");
        CardPack pack = new CardPack(0, List.of(card1), 1, false);

        assertThrows(IllegalArgumentException.class, () -> {
            pack.removeCardFromPack("nonexistent-id");
        });
    }

    @Test
    void testGetDoubleDraftedFlag() {
        Card card1 = createCard("card-1", "Alpha");
        CardPack pack = new CardPack(0, List.of(card1), 1, false);
        assertFalse(pack.getDoubleDraftedFlag());
    }

    @Test
    void testOriginalCardsInPack() {
        Card card1 = createCard("card-1", "Alpha");
        CardPack pack = new CardPack(0, List.of(card1), 5, false);
        assertEquals(5, pack.getOriginalCardsInPack());
    }
}
