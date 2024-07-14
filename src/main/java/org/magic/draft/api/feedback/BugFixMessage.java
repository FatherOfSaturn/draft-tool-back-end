package org.magic.draft.api.feedback;

import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "gameID", "gameState" })
public class BugFixMessage {
    private final String gameID;
    private final String playerName;
    private final String message;

    @JsonCreator
    public BugFixMessage(@JsonProperty("gameID") @BsonProperty("gameID") final String gameID,
                         @JsonProperty("playerName") @BsonProperty("playerName") final String playerName,
                         @JsonProperty("message") @BsonProperty("message")final String message) {
        this.gameID = Objects.requireNonNull(gameID, "gameID Required for Game Status");
        this.playerName = Objects.requireNonNull(playerName, "playerName Required for Game status");
        this.message = Objects.requireNonNull(message, "message Required for Game status");
    }

    public String getGameID() {
        return gameID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getMessage() {
        return message;
    }
}