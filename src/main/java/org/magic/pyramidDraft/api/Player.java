package org.magic.pyramidDraft.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardPack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Represents a player in a pyramid draft game. Tracks the player's card packs,
 * drafted cards, remaining double-draft tokens, and merge readiness.
 * The {@link #draftCard(String, int, boolean)} method contains the core draft state machine.
 */
@Getter
@Setter
@EqualsAndHashCode
@JsonPropertyOrder({ "playerName", "cardsDrafted", "cardPacks", "doubleDraftPicksRemaining" })
public class Player {
    private static final Logger LOGGER = LogManager.getLogger(Player.class);
    private final String playerName;
    private final String accountID;
    private List<CardPack> cardPacks;
    @Setter(AccessLevel.NONE)
    private List<Card> cardsDrafted;
    @Setter(AccessLevel.NONE)
    private int doubleDraftPicksRemaining;
    @Setter(AccessLevel.NONE)
    private int currentDraftPack;
    @Setter(AccessLevel.NONE)
    private boolean readyForMerge;

    @JsonCreator
    @BsonCreator
    public Player(@JsonProperty("playerName") @BsonProperty("playerName") 
                  final String playerName, 
                  @JsonProperty("accountID") @BsonProperty("accountID") 
                  final String accountID, 
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
        this.accountID = Objects.requireNonNull(accountID, "account id Required for Player");
        this.cardPacks = Objects.requireNonNull(cardPacks, "card packs Required for Player");
        this.cardsDrafted = Objects.requireNonNullElse(cardsDrafted, new ArrayList<>());
        this.doubleDraftPicksRemaining = Objects.requireNonNull(doubleDraftPicksRemaining, "double picks amount required for Player");
        this.currentDraftPack = Objects.requireNonNullElse(currentDraftPack, 0);
        this.readyForMerge = Objects.requireNonNullElse(readyForMerge, false);
    }

    /**
     * Drafts a card from the specified pack. If using a double-pick token, the player
     * gets to draft from the same pack twice (the pack counter doesn't advance on the
     * first pick, but the token is consumed). After each draft, if the player has
     * exhausted all packs, they are marked as ready for merge.
     *
     * @param cardID     the Scryfall card ID to draft
     * @param packNumber the pack number to draft from
     * @param isDoublePick whether a double-pick token should be consumed
     * @return the drafted {@link Card}
     * @throws IllegalStateException if a double pick is attempted with no tokens remaining
     */
    public Card draftCard(final String cardID, final int packNumber, boolean isDoublePick) {

        if (isDoublePick && doubleDraftPicksRemaining <= 0) {
            throw new IllegalStateException("Attempting to double draft while player has no doubles left.");
        } else if (isDoublePick) {
            // Consume a double-pick token: decrement the pack counter after the draft
            // so the player gets to draft from this pack again on their next turn
            LOGGER.info("Player {} is using a double draft pick token.", this.playerName);
            this.doubleDraftPicksRemaining--;
            this.currentDraftPack--;
            this.cardPacks.get(packNumber).setDoubleDraftedFlag(true);
        }

        CardPack currentDraftPack = cardPacks.stream().filter(pack -> pack.getPackNumber() == packNumber).findFirst().get();
        Card draftedCard = currentDraftPack.removeCardFromPack(cardID);
        this.cardsDrafted.add(draftedCard);
        this.currentDraftPack++;

        if (this.currentDraftPack >= this.cardPacks.size()) {
            LOGGER.info("Setting Player {}, merge status to True", this.playerName);
            this.readyForMerge = true;
        }
        return draftedCard;
    }

    public void resetAfterMerge() {
        this.currentDraftPack = 0;
        this.readyForMerge = false;
    }

    @Override
    public String toString() {
        return "Player [playerName=" + playerName + ", accountID=" + accountID + ", cardPacks#=" + cardPacks.size()
                + ", cardsDrafted#=" + cardsDrafted.size() + ", doubleDraftPicksRemaining=" + doubleDraftPicksRemaining + "]";
    }
}
