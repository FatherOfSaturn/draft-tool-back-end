package org.magic.draft.api.card;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "name", "cards" })
public class Cube {
    private String cubeName;
    private CardsInCube cardsInCube;

    @JsonCreator
    public Cube(@JsonProperty("name") String cubeName, 
                @JsonProperty("cards") CardsInCube cardsInCube) {
        this.cubeName = Objects.requireNonNull(cubeName, "cubeName Required for cube");
        this.cardsInCube = Objects.requireNonNull(cardsInCube, "cardsInCube Required for cube");
    }

    public String getName() {
        return cubeName;
    }

    public CardsInCube getCards() {
        return cardsInCube;
    }

    public void setCards(CardsInCube cardsInCube) {
        this.cardsInCube = cardsInCube;
    }
}
