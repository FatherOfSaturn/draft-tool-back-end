package org.magic.lobbyService.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for joining an existing lobby.
 *
 * @param accountID   the account ID of the joining player, or {@code null} for anonymous guests
 * @param displayName the display name of the joining player
 */
public record JoinLobbyRequest(
    @JsonProperty("accountID") String accountID,
    @JsonProperty("displayName") String displayName
) {

    public JoinLobbyRequest {
        Objects.requireNonNull(displayName, "displayName is required for JoinLobbyRequest");
    }
}
