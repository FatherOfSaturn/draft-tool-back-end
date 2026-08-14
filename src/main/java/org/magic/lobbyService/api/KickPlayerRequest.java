package org.magic.lobbyService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for kicking a player from a lobby.
 * The caller proves host identity by presenting their own lobby token and
 * identifies the target by {@code slotIndex}; the target's player token is
 * resolved server-side and is never exposed to clients.
 *
 * @param playerToken     the caller's own lobby token (must be the host's token)
 * @param targetSlotIndex the slot index of the player to kick
 */
public record KickPlayerRequest(
    @JsonProperty("playerToken") @NotBlank @Size(max = 64) String playerToken,
    @JsonProperty("targetSlotIndex") @NotNull @Min(0) @Max(99) Integer targetSlotIndex
) {
}
