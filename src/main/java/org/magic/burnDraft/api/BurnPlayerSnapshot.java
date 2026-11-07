package org.magic.burnDraft.api;

import java.util.List;

import org.magic.common.card.Card;
import org.magic.common.card.CardPack;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The per-player view of an in-progress burn draft: the packs the player holds
 * and the cards they have drafted and burned so far.
 */
@JsonPropertyOrder({ "playerName", "activeCardPacks", "cardsDrafted", "cardsBurned", "cardsLeftToDraft" })
public record BurnPlayerSnapshot(
    @JsonProperty("playerName") String playerName,
    @JsonProperty("activeCardPacks") List<CardPack> activeCardPacks,
    @JsonProperty("cardsDrafted") List<Card> cardsDrafted,
    @JsonProperty("cardsBurned") List<Card> cardsBurned,
    @JsonProperty("cardsLeftToDraft") int cardsLeftToDraft
) {
}
