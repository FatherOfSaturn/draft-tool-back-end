package org.magic.lobbyService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for leaving a lobby.
 * The caller is identified by their own {@code playerToken}, which is assigned
 * on create or join. Possession of the token is the proof of identity — a raw
 * {@code accountID} alone is never sufficient to remove a player.
 *
 * @param playerToken the caller's own lobby token
 */
public record LeaveLobbyRequest(
    @JsonProperty("playerToken") @NotBlank @Size(max = 64) String playerToken
) {
}
