package org.magic.pyramidDraft.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardDetails;
import org.magic.pyramidDraft.api.card.CardsInCube;

class CardsInCubeTest {

    private Card createCard(String cardID, String name) {
        CardDetails details = new CardDetails("set", "set_name", cardID,
            "https://example.com/small.jpg", "https://example.com/normal.jpg",
            null, name, "Creature", 1, List.of("1"));
        return new Card(name, details, cardID, 1, "Creature");
    }

    private List<Card> createMutableList(Card... cards) {
        return new java.util.ArrayList<>(List.of(cards));
    }

    @Test
    void testDrawCardsFromCube() {
        CardsInCube cic = new CardsInCube(createMutableList(
            createCard("c1", "Alpha"),
            createCard("c2", "Beta"),
            createCard("c3", "Gamma"),
            createCard("c4", "Delta"),
            createCard("c5", "Epsilon")
        ));
        cic.shuffleMainboard();

        List<Card> drawn = cic.drawCardsFromCube(3);
        assertEquals(3, drawn.size());
    }

    @Test
    void testDrawAllCards() {
        CardsInCube cic = new CardsInCube(createMutableList(
            createCard("c1", "Alpha"),
            createCard("c2", "Beta"),
            createCard("c3", "Gamma")
        ));
        cic.shuffleMainboard();

        List<Card> drawn = cic.drawCardsFromCube(3);
        assertEquals(3, drawn.size());
    }

    @Test
    void testDrawMoreThanAvailableThrows() {
        CardsInCube cic = new CardsInCube(createMutableList(
            createCard("c1", "Alpha"),
            createCard("c2", "Beta")
        ));
        cic.shuffleMainboard();

        cic.drawCardsFromCube(2);
        assertThrows(IllegalStateException.class, () -> {
            cic.drawCardsFromCube(1);
        });
    }

    @Test
    void testShuffleMainboardChangesOrder() {
        CardsInCube cic = new CardsInCube(createMutableList(
            createCard("c1", "Alpha"),
            createCard("c2", "Beta"),
            createCard("c3", "Gamma"),
            createCard("c4", "Delta"),
            createCard("c5", "Epsilon")
        ));
        cic.shuffleMainboard();

        List<Card> preShuffle = new java.util.ArrayList<>(cic.getMainboard());
        cic.shuffleMainboard();
        List<Card> postShuffle = new java.util.ArrayList<>(cic.getMainboard());

        assertNotEquals(preShuffle, postShuffle);
    }

    @Test
    void testMainboardInitiallyHasCorrectSize() {
        CardsInCube cic = new CardsInCube(createMutableList(
            createCard("c1", "Alpha"),
            createCard("c2", "Beta")
        ));
        assertEquals(2, cic.getMainboard().size());
    }

    @Test
    void testConstructorThrowsOnNull() {
        assertThrows(NullPointerException.class, () -> {
            new CardsInCube(null);
        });
    }

    @Test
    void testDrawZeroCards() {
        List<Card> cards = List.of(
            createCard("c1", "Alpha")
        );
        CardsInCube cic = new CardsInCube(cards);
        cic.shuffleMainboard();

        List<Card> drawn = cic.drawCardsFromCube(0);
        assertTrue(drawn.isEmpty());
    }
}
