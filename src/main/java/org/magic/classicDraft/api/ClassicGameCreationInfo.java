package org.magic.classicDraft.api;

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

public record ClassicGameCreationInfo(
    @JsonProperty("cubeID")
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "cubeID may only contain letters, digits, '.', '_' and '-'")
    String cubeID,
    @JsonProperty("players") @NotNull @Size(min = 4, max = 12) List<@Valid ClassicPlayerCreationInfo> players,
    @JsonProperty("numberOfPacks") @Min(1) @Max(300) int numberOfPacks,
    @JsonProperty("cardsPerPack") @Min(1) @Max(25) int cardsPerPack,
    @JsonProperty("packsPerPlayer") @Min(1) @Max(30) int packsPerPlayer
) {

    public ClassicGameCreationInfo {
        Objects.requireNonNull(cubeID, "cubeID Required for ClassicGameCreationInfo");
        Objects.requireNonNull(players, "players Required for ClassicGameCreationInfo");
        if (players.size() < 4 || players.size() > 12) {
            throw new IllegalArgumentException("Number of players must be between 4 and 12, got " + players.size());
        }
        if (cardsPerPack < players.size() || cardsPerPack > 25) {
            throw new IllegalArgumentException("cardsPerPack must be between " + players.size() + " and 25, got " + cardsPerPack);
        }
        if (numberOfPacks != players.size() * packsPerPlayer) {
            throw new IllegalArgumentException("numberOfPacks (" + numberOfPacks
                + ") must equal players.size() * packsPerPlayer (" + players.size() + " * " + packsPerPlayer + " = "
                + (players.size() * packsPerPlayer) + ")");
        }
    }
}
