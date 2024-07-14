package org.magic.draft.api;

import java.util.List;
import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "gameID", "players", "gameState" })
public class GameInfo {
    private final String gameID;
    private List<Player> playerStates;
    private GameState gameState;

    @JsonCreator
    @BsonCreator
    public GameInfo(@JsonProperty("gameID") @BsonProperty("gameID") final String gameID,
                    @JsonProperty("players") @BsonProperty("players") final List<Player> playerStates,
                    @JsonProperty("gameState") @BsonProperty("gameState") final GameState gameState) {
        this.gameID = Objects.requireNonNull(gameID, "gameID Required for Game Info");
        this.playerStates = Objects.requireNonNull(playerStates, "player list Required for Game Info");
        this.gameState = Objects.requireNonNull(gameState, "gameState flag Required for Game Info");
    }

    public String getGameID() {
        return gameID;
    }

    public List<Player> getPlayers() {
        return playerStates;
    }

    public void updatePlayers(final List<Player> players) {
        this.playerStates = players;
    }

    public void setGameState(final GameState state) {
        this.gameState = state;
    }

    public GameState getGameState() {
        return this.gameState;
    }

    @Override
    public String toString() {
        if (playerStates.size() == 2) {
            return "GameInfo \n[gameID=" + this.gameID 
                    + ", \n\tplayer1=" + this.playerStates.get(0).toString() 
                    + ", \n\tplayer2=" + this.playerStates.get(1).toString() 
                    + ", \n\tGameState= " + this.gameState + "\n]";
        }

        return "GameInfo [gameID=" + gameID + ", playerStates=" + playerStates + "]";
    }
}