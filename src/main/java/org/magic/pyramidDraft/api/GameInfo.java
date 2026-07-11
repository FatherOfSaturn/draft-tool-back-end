package org.magic.pyramidDraft.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Represents the full state of a pyramid draft game, including the game ID,
 * both players' states, the current game state, and account ownership info.
 * Persisted to MongoDB and serialized to JSON for the frontend.
 */
@Getter
@Setter
@EqualsAndHashCode
@JsonPropertyOrder({ "gameID", "cubeID", "players", "gameState", "createdAt" })
public class GameInfo {
    private final String gameID;
    private final String cubeID;
    @JsonProperty("players")
    @BsonProperty("players")
    @Getter(AccessLevel.NONE)
    private List<Player> playerStates;
    private GameState gameState;
    private String accountID;
    private String partnerAccountID;
    private String accountName;
    private final Instant createdAt;

    @JsonCreator
    @BsonCreator
    public GameInfo(@JsonProperty("gameID") @BsonProperty("gameID") final String gameID,
                    @JsonProperty("cubeID") @BsonProperty("cubeID") final String cubeID,
                    @JsonProperty("players") @BsonProperty("players") final List<Player> playerStates,
                    @JsonProperty("gameState") @BsonProperty("gameState") final GameState gameState,
                    @JsonProperty("accountID") @BsonProperty("accountID") final String accountID,
                    @JsonProperty("partnerAccountID") @BsonProperty("partnerAccountID") final String partnerAccountID,
                    @JsonProperty("accountName") @BsonProperty("accountName") final String accountName,
                    @JsonProperty("createdAt") @BsonProperty("createdAt") final Instant createdAt) {
        this.gameID = Objects.requireNonNull(gameID, "gameID Required for Game Info");
        this.cubeID = cubeID;
        this.playerStates = Objects.requireNonNull(playerStates, "player list Required for Game Info");
        this.gameState = Objects.requireNonNull(gameState, "gameState flag Required for Game Info");
        this.accountID = accountID;
        this.partnerAccountID = partnerAccountID;
        this.accountName = accountName;
        this.createdAt = createdAt;
    }

    public List<Player> getPlayers() {
        return playerStates;
    }

    public void updatePlayers(final List<Player> players) {
        this.playerStates = players;
    }

    @Override
    public String toString() {
        if (playerStates.size() == 2) {
            return "GameInfo \n[gameID=" + this.gameID 
                    + ", \n\tplayer1=" + this.playerStates.get(0)
                    + ", \n\tplayer2=" + this.playerStates.get(1)
                    + ", \n\tGameState= " + this.gameState + "\n]";
        }

        return "GameInfo [gameID=" + gameID + ", playerStates=" + playerStates + "]";
    }
}
