package org.magic.common.game;

import java.util.Objects;

import org.magic.pyramidDraft.api.GameState;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Simple status response returned after game lifecycle operations (merge, end).
 * Contains the game ID and the resulting {@link GameState}.
 *
 * TODO(PR-3): the pyramid GameState reference keeps a temporary
 * common -> pyramid edge; both draft-state enums serialize identically by name,
 * so this can be generified or unified without changing the wire format.
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
