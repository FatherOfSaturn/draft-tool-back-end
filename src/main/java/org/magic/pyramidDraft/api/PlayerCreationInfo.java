package org.magic.pyramidDraft.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload containing the name and ID for creating a player in a new game.
 */
public record PlayerCreationInfo(
    @JsonProperty("name")
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "name must not contain control characters")
    String playerName,
    @JsonProperty("accountID") @NotBlank @Size(max = 100) String accountID,
    @JsonProperty("playerToken") @Size(max = 64) String playerToken
) {

    public PlayerCreationInfo {
        Objects.requireNonNull(playerName, "name required for incoming Player");
        Objects.requireNonNull(accountID, "accountID required for player");
    }
}
