package org.magic.classicDraft.api;

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
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardPack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@Getter
@Setter
@EqualsAndHashCode
@JsonPropertyOrder({ "playerName", "draftOrderNumber", "cardsDrafted", "dealtCardPacks", "activeCardPacks" })
public class ClassicPlayer {
    private static final Logger LOGGER = LogManager.getLogger(ClassicPlayer.class);

    private final String playerName;
    private final String accountID;
    private final int draftOrderNumber;
    private List<CardPack> dealtCardPacks;
    private List<CardPack> activeCardPacks;
    private List<Card> cardsDrafted;

    @JsonCreator
    @BsonCreator
    public ClassicPlayer(
            @JsonProperty("playerName") @BsonProperty("playerName") final String playerName,
            @JsonProperty("accountID") @BsonProperty("accountID") final String accountID,
            @JsonProperty("draftOrderNumber") @BsonProperty("draftOrderNumber") final int draftOrderNumber,
            @JsonProperty("dealtCardPacks") @BsonProperty("dealtCardPacks") final List<CardPack> dealtCardPacks,
            @JsonProperty("activeCardPacks") @BsonProperty("activeCardPacks") final List<CardPack> activeCardPacks,
            @JsonProperty("cardsDrafted") @BsonProperty("cardsDrafted") final List<Card> cardsDrafted) {
        this.playerName = Objects.requireNonNull(playerName, "playerName Required for ClassicPlayer");
        this.accountID = accountID;
        this.draftOrderNumber = draftOrderNumber;
        this.dealtCardPacks = Objects.requireNonNull(dealtCardPacks, "dealtCardPacks Required for ClassicPlayer");
        this.activeCardPacks = Objects.requireNonNullElseGet(activeCardPacks, ArrayList::new);
        this.cardsDrafted = Objects.requireNonNullElse(cardsDrafted, new ArrayList<>());
    }

    @Override
    public String toString() {
        return "ClassicPlayer [playerName=" + playerName + ", accountID=" + accountID
                + ", draftOrderNumber=" + draftOrderNumber + ", dealtCardPacks#=" + dealtCardPacks.size()
                + ", activeCardPacks#=" + activeCardPacks.size()
                + ", cardsDrafted#=" + cardsDrafted.size() + "]";
    }
}
