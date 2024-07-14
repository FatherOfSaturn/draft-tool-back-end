package org.magic.draft.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "gameID", "gameState" })
public class GameStatusMessage {
    private final String gameID;
    private final GameState gameState;

    @JsonCreator
    public GameStatusMessage(@JsonProperty("gameID") final String gameID,
                       @JsonProperty("gameState") final GameState gameState) {
        this.gameID = Objects.requireNonNull(gameID, "gameID Required for Game Status");
        this.gameState = Objects.requireNonNull(gameState, "gameState Required for Game status");
    }

    public String getGameID() {
        return gameID;
    }

    public GameState getGameState() {
        return this.gameState;
    }

    @Override
    public String toString() {
        return "MergeStatus [gameID=" + gameID + ", gameState=" + gameState + "]";
    }
}