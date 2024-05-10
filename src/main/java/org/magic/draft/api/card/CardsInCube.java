package org.magic.draft.api.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "mainboard" })
public class CardsInCube {
    private static final Logger LOGGER = LogManager.getLogger(CardsInCube.class);

    private List<Card> mainboard;
    private Iterator<Card> cardIterator;

    @JsonCreator
    public CardsInCube(@JsonProperty("mainboard") final List<Card> mainboard) {
        this.mainboard = Objects.requireNonNull(mainboard, "List of Cards Required for mainboard");
    }

    public List<Card> drawCardsFromCube(final int numberOfCardsToDraw) {
        LOGGER.info("Attempting to Draw {} from the cube.", numberOfCardsToDraw);
        List<Card> cards = new ArrayList<>();

        for(int counter = 0; counter < numberOfCardsToDraw; counter++) {
            if (cardIterator.hasNext()) {
                cards.add(cardIterator.next());
            }
            else {
                throw new Error("Attempted to draw a card from cube and was unable to find the next card.");
            }
        }
        return cards;
    }

    public void shuffleMainboard() {
        LOGGER.info("Shuffling Cube.");
        Collections.shuffle(this.mainboard);
        cardIterator = mainboard.iterator();
    }

    public List<Card> getMainboard() {
        return mainboard;
    }

    public void setMainboard(List<Card> mainboard) {
        this.mainboard = mainboard;
    }
}