package org.magic.pyramidDraft.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardDetails;
import org.magic.pyramidDraft.api.card.CardPack;

class PlayerTest {

    private CardDetails createDetails(String name, String scryfallId) {
        return new CardDetails("set", "set_name", scryfallId,
            "https://example.com/small.jpg", "https://example.com/normal.jpg",
            null, name, "Creature", 1, List.of("1"));
    }

    private Card createCard(String cardID, String name) {
        CardDetails details = createDetails(name, cardID);
        return new Card(name, details, cardID, 1, "Creature");
    }

    private CardPack createPack(int packNumber, int size) {
        List<Card> cards = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            cards.add(createCard("card-" + packNumber + "-" + i, "Card " + packNumber + "-" + i));
        }
        return new CardPack(packNumber, cards, size, false);
    }

    @Test
    void testDraftCardNormalPick() {
        List<CardPack> packs = List.of(createPack(0, 3), createPack(1, 3));
        Player player = new Player("TestPlayer", "player-1", packs, 0, null, 0, false);

        assertEquals(0, player.getCardsDrafted().size());
        assertEquals(0, player.getCurrentDraftPack());

        Card drafted = player.draftCard("card-0-1", 0, false);
        assertNotNull(drafted);
        assertEquals("Card 0-1", drafted.getName());
        assertEquals(1, player.getCardsDrafted().size());
        assertEquals(1, player.getCurrentDraftPack());
    }

    @Test
    void testDraftCardExhaustsPacksSetsReadyForMerge() {
        List<CardPack> packs = new java.util.ArrayList<>(List.of(createPack(0, 3), createPack(1, 3)));
        Player player = new Player("TestPlayer", "player-1", packs, 0, null, 0, false);

        assertFalse(player.isReadyForMerge());

        player.draftCard("card-0-0", 0, false);
        assertFalse(player.isReadyForMerge());

        player.draftCard("card-0-1", 0, false);
        assertTrue(player.isReadyForMerge());

        assertEquals(2, player.getCardsDrafted().size());
    }

    @Test
    void testDoubleDraftDecrementsRemaining() {
        List<CardPack> packs = List.of(createPack(0, 5), createPack(1, 5));
        Player player = new Player("TestPlayer", "player-1", packs, 2, null, 0, false);

        assertEquals(2, player.getDoubleDraftPicksRemaining());

        player.draftCard("card-0-0", 0, true);

        assertEquals(1, player.getDoubleDraftPicksRemaining());
    }

    @Test
    void testDoubleDraftWithNoRemainingThrows() {
        List<CardPack> packs = List.of(createPack(0, 3));
        Player player = new Player("TestPlayer", "player-1", packs, 0, null, 0, false);

        assertThrows(IllegalStateException.class, () -> {
            player.draftCard("card-0-0", 0, true);
        });
    }

    @Test
    void testDraftInvalidCardIdThrows() {
        List<CardPack> packs = List.of(createPack(0, 3));
        Player player = new Player("TestPlayer", "player-1", packs, 0, null, 0, false);

        assertThrows(IllegalArgumentException.class, () -> {
            player.draftCard("nonexistent-card", 0, false);
        });
    }

    @Test
    void testResetAfterMerge() {
        List<CardPack> packs = List.of(createPack(0, 3));
        Player player = new Player("TestPlayer", "player-1", packs, 0, null, 0, false);

        player.draftCard("card-0-0", 0, false);
        player.draftCard("card-0-1", 0, false);
        player.draftCard("card-0-2", 0, false);
        assertTrue(player.isReadyForMerge());
        assertEquals(3, player.getCurrentDraftPack());

        player.resetAfterMerge();

        assertFalse(player.isReadyForMerge());
        assertEquals(0, player.getCurrentDraftPack());
    }
}
