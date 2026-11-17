package org.magic.burnDraft.api;

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
 * Request payload for creating a burn draft game.
 *
 * <p>Each turn a player drafts {@code cardsDraftedPerPack} cards and burns
 * {@code cardsBurnedPerPack} cards from the pack they receive, then passes it
 * on. When a pack cannot supply a full turn, {@code remainderPolicy} decides
 * whether the leftovers beyond the draft quota are burned or drafted.</p>
 */
public record BurnGameCreationInfo(
    @JsonProperty("cubeID")
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "cubeID may only contain letters, digits, '.', '_' and '-'")
    String cubeID,
    @JsonProperty("players") @NotNull @Size(min = 2, max = 12) List<@Valid BurnPlayerCreationInfo> players,
    @JsonProperty("numberOfPacks") @Min(1) @Max(300) int numberOfPacks,
    @JsonProperty("cardsPerPack") @Min(1) @Max(25) int cardsPerPack,
    @JsonProperty("packsPerPlayer") @Min(1) @Max(30) int packsPerPlayer,
    @JsonProperty("cardsDraftedPerPack") @Min(1) @Max(25) int cardsDraftedPerPack,
    @JsonProperty("cardsBurnedPerPack") @Min(0) @Max(25) int cardsBurnedPerPack,
    @JsonProperty("remainderPolicy") @NotNull BurnRemainderPolicy remainderPolicy
) {

    public BurnGameCreationInfo {
        Objects.requireNonNull(cubeID, "cubeID Required for BurnGameCreationInfo");
        Objects.requireNonNull(players, "players Required for BurnGameCreationInfo");
        Objects.requireNonNull(remainderPolicy, "remainderPolicy Required for BurnGameCreationInfo");
        if (players.size() < 2 || players.size() > 12) {
            throw new IllegalArgumentException("Number of players must be between 2 and 12, got " + players.size());
        }
        if (cardsPerPack < 1 || cardsPerPack > 25) {
            throw new IllegalArgumentException("cardsPerPack must be between 1 and 25, got " + cardsPerPack);
        }
        if (packsPerPlayer < 1) {
            throw new IllegalArgumentException("packsPerPlayer must be at least 1, got " + packsPerPlayer);
        }
        if (cardsDraftedPerPack < 1) {
            throw new IllegalArgumentException("cardsDraftedPerPack must be at least 1, got " + cardsDraftedPerPack);
        }
        if (cardsBurnedPerPack < 0) {
            throw new IllegalArgumentException("cardsBurnedPerPack must not be negative, got " + cardsBurnedPerPack);
        }
        if (cardsDraftedPerPack + cardsBurnedPerPack > cardsPerPack) {
            throw new IllegalArgumentException("cardsDraftedPerPack + cardsBurnedPerPack ("
                + (cardsDraftedPerPack + cardsBurnedPerPack) + ") must not exceed cardsPerPack (" + cardsPerPack + ")");
        }
        if (numberOfPacks != players.size() * packsPerPlayer) {
            throw new IllegalArgumentException("numberOfPacks (" + numberOfPacks
                + ") must equal players.size() * packsPerPlayer (" + players.size() + " * " + packsPerPlayer + " = "
                + (players.size() * packsPerPlayer) + ")");
        }
    }
}
