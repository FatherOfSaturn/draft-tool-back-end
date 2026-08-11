package org.magic.lobbyService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for kicking a player from a lobby.
 *
 * @param hostAccountID the account ID of the requesting host
 * @param playerToken   the token of the player to kick
 */
public record KickPlayerRequest(
    @JsonProperty("hostAccountID") String hostAccountID,
    @JsonProperty("playerToken") String playerToken
) {
}
