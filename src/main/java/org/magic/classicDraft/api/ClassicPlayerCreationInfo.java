package org.magic.classicDraft.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClassicPlayerCreationInfo(
    @JsonProperty("name")
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "name must not contain control characters")
    String playerName,
    @JsonProperty("accountID") @Size(max = 100) String accountID,
    @JsonProperty("playerToken") @Size(max = 64) String playerToken
) {
    public ClassicPlayerCreationInfo {
        Objects.requireNonNull(playerName, "name required for ClassicPlayerCreationInfo");
    }
}
