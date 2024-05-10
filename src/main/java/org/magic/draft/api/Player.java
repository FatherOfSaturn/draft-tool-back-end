package org.magic.draft.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardPack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "playerName", "cardPacks", "doubleDraftPicksRemaining" })
public class Player {
    private static final Logger LOGGER = LogManager.getLogger(Player.class);
    private final String playerName;
    private List<CardPack> cardPacks;
    private List<Card> cardsDrafted;
    private int doubleDraftPicksRemaining;

    @JsonCreator
    public Player(@JsonProperty("playerName") final String playerName, 
                  @JsonProperty("cardPacks") final List<CardPack> cardPacks, 
                  @JsonProperty("doubleDraftPicksRemaining") final int doubleDraftPicksRemaining) {
        this.playerName = Objects.requireNonNull(playerName, "player name Required for Player");
        this.cardPacks = Objects.requireNonNull(cardPacks, "card packs Required for Player");
        this.cardsDrafted = new ArrayList<>();
        this.doubleDraftPicksRemaining = Objects.requireNonNull(doubleDraftPicksRemaining, "double picks amount required for Player");
    }

    public Card draftCard(final String cardID, final int packNumber, boolean isDoublePick) {

        if (isDoublePick && doubleDraftPicksRemaining <= 0) {
            throw new Error("Attempting to double draft while player has no doubles left.");
        } else if (isDoublePick) {
            LOGGER.info("Player {} is using a double draft pick token.", this.playerName);
            this.doubleDraftPicksRemaining--;
        }

        Card draftedCard = cardPacks.get(packNumber).removeCardFromPack(cardID);
        this.cardsDrafted.add(draftedCard);
        return draftedCard;
    }

    public String getPlayerName() {
        return playerName;
    }

    public List<CardPack> getCardPacks() {
        return cardPacks;
    }

    public List<Card> getCardsDrafted() {
        return cardsDrafted;
    }

    public int getDoubleDraftPicksRemaining() {
        return doubleDraftPicksRemaining;
    }

    public void setCards(final List<CardPack> newCardPacks) {
        this.cardPacks = newCardPacks;
    }
}