package org.magic.pyramidDraft.api.feedback;

import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "gameID", "gameState" })
public record BugFixMessage(
    @JsonProperty("gameID") @BsonProperty("gameID") String gameID,
    @JsonProperty("playerName") @BsonProperty("playerName") String playerName,
    @JsonProperty("message") @BsonProperty("message") String message
) {

    public BugFixMessage {
        Objects.requireNonNull(gameID, "gameID Required for Game Status");
        Objects.requireNonNull(playerName, "playerName Required for Game status");
        Objects.requireNonNull(message, "message Required for Game status");
    }
}
