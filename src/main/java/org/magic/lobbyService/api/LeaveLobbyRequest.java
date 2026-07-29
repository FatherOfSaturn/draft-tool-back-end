package org.magic.lobbyService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for leaving a lobby.
 * Identify the player by either their {@code accountID} (for authenticated users)
 * or their {@code playerToken} (for anonymous guests).
 *
 * @param accountID   the account ID of the leaving player, or {@code null}
 * @param playerToken the player token assigned on join, or {@code null}
 */
public record LeaveLobbyRequest(
    @JsonProperty("accountID") String accountID,
    @JsonProperty("playerToken") String playerToken
) {
}
