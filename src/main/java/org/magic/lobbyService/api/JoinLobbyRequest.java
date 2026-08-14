package org.magic.lobbyService.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for joining an existing lobby.
 *
 * @param accountID   the account ID of the joining player, or {@code null} for anonymous guests
 * @param displayName the display name of the joining player
 */
public record JoinLobbyRequest(
    @JsonProperty("accountID") @Size(max = 100) String accountID,
    @JsonProperty("displayName")
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "displayName must not contain control characters")
    String displayName
) {

    public JoinLobbyRequest {
        Objects.requireNonNull(displayName, "displayName is required for JoinLobbyRequest");
    }
}
