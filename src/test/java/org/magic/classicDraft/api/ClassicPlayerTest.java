package org.magic.classicDraft.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardDetails;
import org.magic.pyramidDraft.api.card.CardPack;

class ClassicPlayerTest {

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
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            cards.add(createCard("card-" + packNumber + "-" + i, "Card " + packNumber + "-" + i));
        }
        return new CardPack(packNumber, cards, size, false);
    }

    private CardPack createDealtPack(int packNumber, int size) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            cards.add(createCard("dealt-" + packNumber + "-" + i, "Dealt " + packNumber + "-" + i));
        }
        return new CardPack(packNumber, cards, size, false);
    }

    @Test
    void testPlayerProperties() {
        List<CardPack> dealt = List.of(createDealtPack(0, 6));
        ClassicPlayer player = new ClassicPlayer("Alice", "acct-alice", 2, dealt, null, null);

        assertEquals("Alice", player.getPlayerName());
        assertEquals("acct-alice", player.getAccountID());
        assertEquals(2, player.getDraftOrderNumber());
        assertEquals(1, player.getDealtCardPacks().size());
        assertEquals(0, player.getActiveCardPacks().size());
        assertEquals(0, player.getCardsDrafted().size());
    }

    @Test
    void testAccountIdCanBeNull() {
        List<CardPack> dealt = List.of(createDealtPack(0, 6));
        ClassicPlayer player = new ClassicPlayer("Bob", null, 0, dealt, null, null);

        assertNull(player.getAccountID());
        assertEquals("Bob", player.getPlayerName());
    }

    @Test
    void testActiveCardPacksQueueBehavior() {
        List<CardPack> dealt = List.of(createDealtPack(0, 6), createDealtPack(1, 6));
        ClassicPlayer player = new ClassicPlayer("Alice", "acct-alice", 0, dealt, null, null);

        assertEquals(0, player.getActiveCardPacks().size());

        player.getActiveCardPacks().add(dealt.get(0));
        assertEquals(1, player.getActiveCardPacks().size());

        CardPack front = player.getActiveCardPacks().remove(0);
        assertEquals(0, front.getPackNumber());
        assertEquals(0, player.getActiveCardPacks().size());

        player.getActiveCardPacks().add(dealt.get(1));
        player.getActiveCardPacks().add(dealt.get(0));
        assertEquals(2, player.getActiveCardPacks().size());

        front = player.getActiveCardPacks().remove(0);
        assertEquals(1, front.getPackNumber());
        assertEquals(1, player.getActiveCardPacks().size());
    }

    @Test
    void testDealtCardPacksArePreserved() {
        List<CardPack> dealt = new ArrayList<>();
        dealt.add(createDealtPack(0, 6));
        dealt.add(createDealtPack(1, 6));
        ClassicPlayer player = new ClassicPlayer("Alice", "acct-alice", 0, dealt, null, null);

        assertEquals(2, player.getDealtCardPacks().size());
        assertEquals(6, player.getDealtCardPacks().get(0).getCardsInPack().size());
        assertEquals(6, player.getDealtCardPacks().get(1).getCardsInPack().size());

        List<Card> activeCards = new ArrayList<>(player.getDealtCardPacks().get(0).getCardsInPack());
        CardPack activePack = new CardPack(
                player.getDealtCardPacks().get(0).getPackNumber(),
                activeCards,
                player.getDealtCardPacks().get(0).getOriginalCardsInPack(),
                player.getDealtCardPacks().get(0).getDoubleDraftedFlag());
        player.getActiveCardPacks().add(activePack);
        player.getActiveCardPacks().get(0).removeCardFromPack("dealt-0-0");

        assertEquals(5, player.getActiveCardPacks().get(0).getCardsInPack().size(),
                "activeCardPacks pack should lose the card");

        assertEquals(6, player.getDealtCardPacks().get(0).getCardsInPack().size(),
                "dealtCardPacks should not be modified after deep copy into activeCardPacks");
    }

    @Test
    void testDealtCardPacksHaveDifferentCardIds() {
        List<CardPack> dealt = new ArrayList<>();
        dealt.add(createDealtPack(0, 6));
        ClassicPlayer player = new ClassicPlayer("Alice", "acct-alice", 0, dealt, null, null);

        boolean hasDealtPrefix = player.getDealtCardPacks().get(0).getCardsInPack().stream()
                .allMatch(c -> c.getCardID().startsWith("dealt-"));
        assertTrue(hasDealtPrefix);
    }
}
