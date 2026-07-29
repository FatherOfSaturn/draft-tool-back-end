package org.magic.lobbyService.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for a lobby host to start the game.
 *
 * @param hostAccountID the account ID of the host requesting the start
 */
public record StartGameRequest(
    @JsonProperty("hostAccountID") String hostAccountID
) {

    public StartGameRequest {
        Objects.requireNonNull(hostAccountID, "hostAccountID is required for StartGameRequest");
    }
}
