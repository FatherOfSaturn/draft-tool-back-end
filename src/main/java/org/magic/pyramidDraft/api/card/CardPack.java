package org.magic.pyramidDraft.api.card;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A pack of cards within a pyramid draft. Internally stores cards in a map keyed by
 * card ID for O(1) removal during drafting. Tracks the original card count (before
 * any double-pick removals) and whether the pack has been double-drafted.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@JsonPropertyOrder({ "packNumber", "cardsInPack", "originalCardsInPack" })
public class CardPack {
    private int packNumber;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Map<String, Card> cardsInPack;
    @Setter(AccessLevel.NONE)
    private final int originalCardsInPack;
    @Getter(AccessLevel.NONE)
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

    public List<Card> getCardsInPack() {
        return cardsInPack.values().stream().toList();
    }

    public boolean getDoubleDraftedFlag() {
        return this.doubleDraftedFlag;
    }

    public Card removeCardFromPack(final String cardId) {
        if (cardsInPack.containsKey(cardId)) {
            return cardsInPack.remove(cardId);
        }
        else {
            throw new IllegalArgumentException("Unable to find card " + cardId + " from Card Pack #" + this.packNumber);
        }
    }
}
