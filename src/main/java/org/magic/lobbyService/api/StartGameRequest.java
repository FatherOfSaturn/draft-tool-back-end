package org.magic.lobbyService.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for a lobby host to start the game.
 * The caller proves host identity by presenting their own lobby token;
 * the host {@code playerToken} is never accepted from the request body.
 *
 * @param playerToken the caller's own lobby token (must be the host's token)
 */
public record StartGameRequest(
    @JsonProperty("playerToken") @NotBlank @Size(max = 64) String playerToken
) {

    public StartGameRequest {
        Objects.requireNonNull(playerToken, "playerToken is required for StartGameRequest");
    }
}
