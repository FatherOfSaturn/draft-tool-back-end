package org.magic.draft.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardPack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "playerName", "cardsDrafted", "cardPacks", "doubleDraftPicksRemaining" })
public class Player {
    private static final Logger LOGGER = LogManager.getLogger(Player.class);
    private final String playerName;
    private final String playerID;
    private List<CardPack> cardPacks;
    private List<Card> cardsDrafted;
    private int doubleDraftPicksRemaining;
    private int currentDraftPack;
    private boolean readyForMerge;

    @JsonCreator
    @BsonCreator
    public Player(@JsonProperty("playerName") @BsonProperty("playerName") 
                  final String playerName, 
                  @JsonProperty("playerID") @BsonProperty("playerID") 
                  final String playerID, 
                  @JsonProperty("cardPacks") @BsonProperty("cardPacks") 
                  final List<CardPack> cardPacks, 
                  @JsonProperty("doubleDraftPicksRemaining") @BsonProperty("doubleDraftPicksRemaining") 
                  final int doubleDraftPicksRemaining,
                  @JsonProperty("cardsDrafted") @BsonProperty("cardsDrafted")
                  final List<Card> cardsDrafted,
                  @JsonProperty("currentDraftPack") @BsonProperty("currentDraftPack") 
                  final int currentDraftPack, 
                  @JsonProperty("readyForMerge") @BsonProperty("readyForMerge") 
                  final boolean readyForMerge) {
        this.playerName = Objects.requireNonNull(playerName, "player name Required for Player");
        this.playerID = Objects.requireNonNull(playerID, "player id Required for Player");
        this.cardPacks = Objects.requireNonNull(cardPacks, "card packs Required for Player");
        this.cardsDrafted = Objects.requireNonNullElse(cardsDrafted, new ArrayList<>());
        this.doubleDraftPicksRemaining = Objects.requireNonNull(doubleDraftPicksRemaining, "double picks amount required for Player");
        this.currentDraftPack = Objects.requireNonNullElse(currentDraftPack, 0);
        this.readyForMerge = Objects.requireNonNullElse(readyForMerge, false);
    }

    public Card draftCard(final String cardID, final int packNumber, boolean isDoublePick) {

        if (isDoublePick && doubleDraftPicksRemaining <= 0) {
            throw new Error("Attempting to double draft while player has no doubles left.");
        } else if (isDoublePick) {
            LOGGER.info("Player {} is using a double draft pick token.", this.playerName);
            // Decrement number of doubles left
            this.doubleDraftPicksRemaining--;
            // dirty solution to counteract the moving forward of the pack in the normal use case
            this.currentDraftPack--;
            this.cardPacks.get(packNumber).setDoubleDraftedFlag(true);
        }

        CardPack currentDraftPack = cardPacks.stream().filter(pack -> pack.getPackNumber() == packNumber).findFirst().get();
        Card draftedCard = currentDraftPack.removeCardFromPack(cardID);
        this.cardsDrafted.add(draftedCard);
        this.currentDraftPack++;

        // check to see if this is the players last pick
        // packs are initialized at 0,
        // should make it so after incrementing after the last pack would set us equal to the size
        if (this.currentDraftPack >= this.cardPacks.size()) {
            LOGGER.info("Setting Player {}, merge status to True", this.playerName);
            this.readyForMerge = true;
        }
        return draftedCard;
    }

    /**
     * Merge has completed, reset the draft pack to 0
     * Set Flag for merge to false so no more merges can happen
     */
    public void resetAfterMerge() {
        this.currentDraftPack = 0;
        this.readyForMerge = false;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPlayerID() {
        return playerID;
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

    public void setCardPacks(final List<CardPack> newCardPacks) {
        this.cardPacks = newCardPacks;
    }

    public int getCurrentDraftPack() {
        return currentDraftPack;
    }

    public boolean isReadyForMerge() {
        return readyForMerge;
    }

    @Override
    public String toString() {
        return "Player [playerName=" + playerName + ", playerID=" + playerID + ", cardPacks#=" + cardPacks.size()
                + ", cardsDrafted#=" + cardsDrafted.size() + ", doubleDraftPicksRemaining=" + doubleDraftPicksRemaining + "]";
    }
}