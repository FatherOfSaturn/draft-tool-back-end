package org.magic.burnDraft.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.magic.common.card.Card;
import org.magic.common.card.CardPack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A player in a burn draft. Mirrors the classic draft player and additionally
 * tracks the cards they burned (removed from circulation) alongside the cards
 * they drafted.
 */
@Getter
@Setter
@EqualsAndHashCode
@JsonPropertyOrder({ "playerName", "draftOrderNumber", "cardsDrafted", "cardsBurned",
                     "dealtCardPacks", "activeCardPacks" })
public class BurnPlayer {
    private static final Logger LOGGER = LogManager.getLogger(BurnPlayer.class);

    private final String playerName;
    private final String accountID;
    private final int draftOrderNumber;
    private List<CardPack> dealtCardPacks;
    private List<CardPack> activeCardPacks;
    private List<Card> cardsDrafted;
    private List<Card> cardsBurned;

    /**
     * Bearer credential used to take turns, fetch draft data, and check draft
     * status. Assigned at game creation (from the lobby token when started via a
     * lobby) and never serialized in API responses — only its owner knows it.
     */
    @BsonProperty("playerToken")
    @JsonIgnore
    private String playerToken;

    @JsonCreator
    @BsonCreator
    public BurnPlayer(
            @JsonProperty("playerName") @BsonProperty("playerName") final String playerName,
            @JsonProperty("accountID") @BsonProperty("accountID") final String accountID,
            @JsonProperty("draftOrderNumber") @BsonProperty("draftOrderNumber") final int draftOrderNumber,
            @JsonProperty("dealtCardPacks") @BsonProperty("dealtCardPacks") final List<CardPack> dealtCardPacks,
            @JsonProperty("activeCardPacks") @BsonProperty("activeCardPacks") final List<CardPack> activeCardPacks,
            @JsonProperty("cardsDrafted") @BsonProperty("cardsDrafted") final List<Card> cardsDrafted,
            @JsonProperty("cardsBurned") @BsonProperty("cardsBurned") final List<Card> cardsBurned) {
        this.playerName = Objects.requireNonNull(playerName, "playerName Required for BurnPlayer");
        this.accountID = accountID;
        this.draftOrderNumber = draftOrderNumber;
        // Defensive copies: the draft state machine mutates these lists (pop/pass
        // packs, append drafted/burned cards) and callers may pass immutable lists.
        this.dealtCardPacks = new ArrayList<>(
                Objects.requireNonNull(dealtCardPacks, "dealtCardPacks Required for BurnPlayer"));
        this.activeCardPacks = activeCardPacks == null ? new ArrayList<>() : new ArrayList<>(activeCardPacks);
        this.cardsDrafted = cardsDrafted == null ? new ArrayList<>() : new ArrayList<>(cardsDrafted);
        this.cardsBurned = cardsBurned == null ? new ArrayList<>() : new ArrayList<>(cardsBurned);
    }

    @Override
    public String toString() {
        return "BurnPlayer [playerName=" + playerName + ", accountID=" + accountID
                + ", draftOrderNumber=" + draftOrderNumber + ", dealtCardPacks#=" + dealtCardPacks.size()
                + ", activeCardPacks#=" + activeCardPacks.size()
                + ", cardsDrafted#=" + cardsDrafted.size()
                + ", cardsBurned#=" + cardsBurned.size() + "]";
    }
}
