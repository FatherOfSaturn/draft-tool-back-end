package org.magic.burnDraft.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A player participating in a burn draft, as supplied when the game is created.
 * The token identifies the player on draft actions; it is never serialized back.
 */
public record BurnPlayerCreationInfo(
    @JsonProperty("name")
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "name must not contain control characters")
    String playerName,
    @JsonProperty("accountID") @Size(max = 100) String accountID,
    @JsonProperty("playerToken") @Size(max = 64) String playerToken
) {
    public BurnPlayerCreationInfo {
        Objects.requireNonNull(playerName, "name required for BurnPlayerCreationInfo");
    }
}
