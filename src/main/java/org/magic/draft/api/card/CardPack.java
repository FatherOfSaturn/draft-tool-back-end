package org.magic.draft.api.card;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "packNumber", "cardsInPack", "originalCardsInPack" })
public class CardPack {
    private int packNumber;
    private Map<String, Card> cardsInPack;
    private final int originalCardsInPack;
    private boolean doubleDraftedFlag;

    @JsonCreator
    @BsonCreator
    public CardPack(@JsonProperty("packNumber") @BsonProperty("packNumber") final int packNumber,
                    @JsonProperty("cardsInPack") @BsonProperty("cardsInPack") final Collection<Card> cardsInPack,
                    @JsonProperty("originalCardsInPack") @BsonProperty("originalCardsInPack") final int originalCardsInPack,
                    @JsonProperty("doubleDraftedFlag") @BsonProperty("doubleDraftedFlag") final boolean doubleDraftedFlag) {
        this.packNumber = Objects.requireNonNull(packNumber, "pack number Required for card Pack");
        this.cardsInPack = cardsInPack.stream()
                                      .collect(Collectors.toMap(Card::getCardID, item -> item));
        this.originalCardsInPack = originalCardsInPack;
        this.doubleDraftedFlag = doubleDraftedFlag;
    }

    public int getPackNumber() {
        return packNumber;
    }

    public void setPackNumber(final int packNumber) {
        this.packNumber = packNumber;
    }

    public boolean getDoubleDraftedFlag() {
        return this.doubleDraftedFlag;
    }

    public void setDoubleDraftedFlag(final boolean doubleDraftedFlag) {
        this.doubleDraftedFlag = doubleDraftedFlag;
    }

    public List<Card> getCardsInPack() {
        return cardsInPack.values().stream().toList();
    }

    public Card removeCardFromPack(final String cardId) {
        if (cardsInPack.containsKey(cardId)) {
            return cardsInPack.remove(cardId);
        }
        else {
            throw new Error("Unable to find card " + cardId + " from Card Pack #" + this.packNumber);
        }
    }

    public int getOriginalCardsInPack() {
        return originalCardsInPack;
    }
}