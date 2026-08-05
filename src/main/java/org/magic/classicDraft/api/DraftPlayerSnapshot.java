package org.magic.classicDraft.api;

import java.util.List;

import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardPack;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The per-player view of an in-progress classic draft. Contains only what the
 * drafting player needs to see: their drafted cards and the pack they are
 * currently selecting from.
 */
@JsonPropertyOrder({ "playerName", "activeCardPacks", "cardsDrafted", "cardsLeftToDraft" })
public record DraftPlayerSnapshot(
    @JsonProperty("playerName") String playerName,
    @JsonProperty("activeCardPacks") List<CardPack> activeCardPacks,
    @JsonProperty("cardsDrafted") List<Card> cardsDrafted,
    @JsonProperty("cardsLeftToDraft") int cardsLeftToDraft
) {}
