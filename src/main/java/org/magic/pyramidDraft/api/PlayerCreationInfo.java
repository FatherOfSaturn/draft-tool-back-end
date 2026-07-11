package org.magic.pyramidDraft.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload containing the name and ID for creating a player in a new game.
 */
public record PlayerCreationInfo(
    @JsonProperty("name") String playerName,
    @JsonProperty("playerID") String playerID
) {

    public PlayerCreationInfo {
        Objects.requireNonNull(playerName, "name required for incoming Player");
        Objects.requireNonNull(playerID, "playerID required for player");
    }
}
