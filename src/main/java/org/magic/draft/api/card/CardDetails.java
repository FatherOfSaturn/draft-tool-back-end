package org.magic.draft.api.card;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "set", "set_name", "scryfall_id", "image_small", "image_normal" })
public class CardDetails {
    private String set;
    private String set_name;
    private String scryfall_id;
    private String image_small;
    private String image_normal;
    private String cardName;

    @JsonCreator
    public CardDetails(@JsonProperty("set") final String set,
                       @JsonProperty("set_name") final String setName,
                       @JsonProperty("scryfall_id") final String scryfallId,
                       @JsonProperty("image_small") final String imageSmall,
                       @JsonProperty("image_normal") final String imageNormal,
                       @JsonProperty("name") final String cardName) {
        this.set = Objects.requireNonNull(set, "set Required for card details");
        this.set_name = Objects.requireNonNull(setName, "set_name Required for card details");
        this.scryfall_id = Objects.requireNonNull(scryfallId, "scryfallId Required for card details");
        this.image_small = Objects.requireNonNull(imageSmall, "imageSmall Required for card details");
        this.image_normal = Objects.requireNonNull(imageNormal, "imageNormal Required for card details");
        this.cardName = Objects.requireNonNull(cardName, "cardName Required for card details");
    }

    public String getSet() {
        return set;
    }

    public void setSet(String set) {
        this.set = set;
    }

    public String getSet_name() {
        return set_name;
    }

    public void setSet_name(String set_name) {
        this.set_name = set_name;
    }

    public String getScryfall_id() {
        return scryfall_id;
    }

    public void setScryfall_id(String scryfall_id) {
        this.scryfall_id = scryfall_id;
    }

    public String getImage_small() {
        return image_small;
    }

    public void setImage_small(String image_small) {
        this.image_small = image_small;
    }

    public String getImage_normal() {
        return image_normal;
    }

    public void setImage_normal(String image_normal) {
        this.image_normal = image_normal;
    }

    public String getCardName() {
        return cardName;
    }
}