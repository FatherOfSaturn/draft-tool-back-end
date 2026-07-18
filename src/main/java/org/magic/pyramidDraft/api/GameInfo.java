package org.magic.pyramidDraft.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

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
    @BsonProperty("players")
    private List<Player> players;
    private GameState gameState;
    private final Instant createdAt;

    @JsonCreator
    @BsonCreator
    public GameInfo(@JsonProperty("gameID") @BsonProperty("gameID") final String gameID,
                    @JsonProperty("cubeID") @BsonProperty("cubeID") final String cubeID,
                    @JsonProperty("players") @BsonProperty("players") final List<Player> players,
                    @JsonProperty("gameState") @BsonProperty("gameState") final GameState gameState,
                    @JsonProperty("createdAt") @BsonProperty("createdAt") final Instant createdAt) {
        this.gameID = Objects.requireNonNull(gameID, "gameID Required for Game Info");
        this.cubeID = cubeID;
        this.players = Objects.requireNonNull(players, "player list Required for Game Info");
        this.gameState = Objects.requireNonNull(gameState, "gameState flag Required for Game Info");
        this.createdAt = createdAt;
    }

    public void updatePlayers(final List<Player> players) {
        this.players = players;
    }

    @Override
    public String toString() {
        if (players.size() == 2) {
            return "GameInfo \n[gameID=" + this.gameID 
                    + ", \n\tplayer1=" + this.players.get(0)
                    + ", \n\tplayer2=" + this.players.get(1)
                    + ", \n\tGameState= " + this.gameState + "\n]";
        }

        return "GameInfo [gameID=" + gameID + ", players=" + players + "]";
    }
}
