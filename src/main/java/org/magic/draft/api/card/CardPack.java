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

@JsonPropertyOrder({ "packNumber", "cardsInPack" })
public class CardPack {
    private int packNumber;
    private Map<String, Card> cardsInPack;
    private final int originalCardsInPackNumber;

    @JsonCreator
    @BsonCreator
    public CardPack(@JsonProperty("packNumber") @BsonProperty("packNumber") final int packNumber,
                    @JsonProperty("cardsInPack") @BsonProperty("cardsInPack")final Collection<Card> cardsInPack) {
        this.packNumber = Objects.requireNonNull(packNumber, "pack number Required for card Pack");
        this.cardsInPack = cardsInPack.stream()
                                      .collect(Collectors.toMap(Card::getCardID, item -> item));
        this.originalCardsInPackNumber = cardsInPack.size();
    }

    public int getPackNumber() {
        return packNumber;
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

    public int getOriginalCardsInPackNumber() {
        return originalCardsInPackNumber;
    }
}