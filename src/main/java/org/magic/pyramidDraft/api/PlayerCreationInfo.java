package org.magic.pyramidDraft.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload containing the name and ID for creating a player in a new game.
 */
public record PlayerCreationInfo(
    @JsonProperty("name") String playerName,
    @JsonProperty("accountID") String accountID
) {

    public PlayerCreationInfo {
        Objects.requireNonNull(playerName, "name required for incoming Player");
        Objects.requireNonNull(accountID, "accountID required for player");
    }
}
