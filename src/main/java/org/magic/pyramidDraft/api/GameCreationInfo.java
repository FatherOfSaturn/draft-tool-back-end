package org.magic.pyramidDraft.api;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new pyramid draft game. Contains the cube ID,
 * player information, and the number of double-draft picks allowed per player.
 */
public record GameCreationInfo(
    @JsonProperty("gameID") @NotBlank @Size(max = 64) String gameID,
    @JsonProperty("cubeID")
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "cubeID may only contain letters, digits, '.', '_' and '-'")
    String cubeID,
    @JsonProperty("players") @NotNull @Size(min = 1, max = 2) List<@Valid PlayerCreationInfo> playerInfo,
    @JsonProperty("numberOfDoubleDraftPicksPerPlayer") @Min(0) @Max(10) int numberOfDoubleDraftPicksPerPlayer
) {

    public GameCreationInfo {
        Objects.requireNonNull(gameID, "gameID Required for Game Info");
        Objects.requireNonNull(cubeID, "cubeID Required for Game Info");
        Objects.requireNonNull(playerInfo, "players Required for Game Info");
    }
}
