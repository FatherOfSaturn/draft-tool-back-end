package org.magic.pyramidDraft.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Simple status response returned after game lifecycle operations (merge, end).
 * Contains the game ID and the resulting {@link GameState}.
 */
@JsonPropertyOrder({ "gameID", "gameState" })
public record GameStatusMessage(
    @JsonProperty("gameID") String gameID,
    @JsonProperty("gameState") GameState gameState
) {

    public GameStatusMessage {
        Objects.requireNonNull(gameID, "gameID Required for Game Status");
        Objects.requireNonNull(gameState, "gameState Required for Game status");
    }
}
