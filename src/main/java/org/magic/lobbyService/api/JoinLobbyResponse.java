package org.magic.lobbyService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response returned when a player successfully joins a lobby.
 * Contains the updated lobby state and the player's authentication token
 * (used for subsequent poll and leave operations).
 *
 * @param lobby       the full updated lobby state
 * @param playerToken the token assigned to the joining player
 */
public record JoinLobbyResponse(
    @JsonProperty("lobby") Lobby lobby,
    @JsonProperty("playerToken") String playerToken
) {
}
