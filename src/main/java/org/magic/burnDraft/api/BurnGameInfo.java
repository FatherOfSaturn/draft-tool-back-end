package org.magic.burnDraft.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.magic.common.game.DraftDirection;
import org.magic.common.game.GameState;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * Full state of a burn draft game. Mirrors the classic draft document and stores
 * the per-game turn configuration so the worker and clients can read it from the
 * game itself.
 */
@Getter
@Setter
@EqualsAndHashCode
@JsonPropertyOrder({ "gameID", "cubeID", "gameType", "players", "gameState", "createdAt",
                     "currentPackIndex", "draftDirection", "cardsDraftedPerPack",
                     "cardsBurnedPerPack", "remainderPolicy" })
@MongoEntity(collection = "BurnGames")
public class BurnGameInfo {
    private final String gameID;
    private final String cubeID;
    private final String gameType;
    private List<BurnPlayer> players;
    private GameState gameState;
    private final Instant createdAt;
    private int currentPackIndex;
    private DraftDirection draftDirection;
    private final int cardsDraftedPerPack;
    private final int cardsBurnedPerPack;
    private final BurnRemainderPolicy remainderPolicy;

    @JsonCreator
    @BsonCreator
    public BurnGameInfo(
            @JsonProperty("gameID") @BsonProperty("gameID") final String gameID,
            @JsonProperty("cubeID") @BsonProperty("cubeID") final String cubeID,
            @JsonProperty("gameType") @BsonProperty("gameType") final String gameType,
            @JsonProperty("players") @BsonProperty("players") final List<BurnPlayer> players,
            @JsonProperty("gameState") @BsonProperty("gameState") final GameState gameState,
            @JsonProperty("createdAt") @BsonProperty("createdAt") final Instant createdAt,
            @JsonProperty("currentPackIndex") @BsonProperty("currentPackIndex") final int currentPackIndex,
            @JsonProperty("draftDirection") @BsonProperty("draftDirection") final DraftDirection draftDirection,
            @JsonProperty("cardsDraftedPerPack") @BsonProperty("cardsDraftedPerPack") final int cardsDraftedPerPack,
            @JsonProperty("cardsBurnedPerPack") @BsonProperty("cardsBurnedPerPack") final int cardsBurnedPerPack,
            @JsonProperty("remainderPolicy") @BsonProperty("remainderPolicy") final BurnRemainderPolicy remainderPolicy) {
        this.gameID = Objects.requireNonNull(gameID, "gameID Required for BurnGameInfo");
        this.cubeID = cubeID;
        this.gameType = Objects.requireNonNullElse(gameType, "burn");
        this.players = Objects.requireNonNull(players, "players Required for BurnGameInfo");
        this.gameState = Objects.requireNonNull(gameState, "gameState Required for BurnGameInfo");
        this.createdAt = createdAt;
        this.currentPackIndex = currentPackIndex;
        this.draftDirection = Objects.requireNonNullElse(draftDirection, DraftDirection.ASCENDING);
        this.cardsDraftedPerPack = Math.max(1, cardsDraftedPerPack);
        this.cardsBurnedPerPack = Math.max(0, cardsBurnedPerPack);
        this.remainderPolicy = Objects.requireNonNullElse(remainderPolicy, BurnRemainderPolicy.BURN_EXTRAS);
    }

    public void updatePlayers(final List<BurnPlayer> players) {
        this.players = players;
    }

    @Override
    public String toString() {
        return "BurnGameInfo [gameID=" + gameID + ", cubeID=" + cubeID + ", gameType=" + gameType
                + ", players#=" + players.size() + ", gameState=" + gameState
                + ", currentPackIndex=" + currentPackIndex + ", draftDirection=" + draftDirection
                + ", cardsDraftedPerPack=" + cardsDraftedPerPack + ", cardsBurnedPerPack=" + cardsBurnedPerPack
                + ", remainderPolicy=" + remainderPolicy + "]";
    }
}
