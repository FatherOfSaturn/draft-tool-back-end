package org.magic.pyramidDraft.api.card;

import java.util.Objects;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@JsonPropertyOrder({ "name", "cards" })
public class Cube {
    @Getter(AccessLevel.NONE)
    private String cubeName;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
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
