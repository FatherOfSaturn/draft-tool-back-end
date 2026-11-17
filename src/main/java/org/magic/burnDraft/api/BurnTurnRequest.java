package org.magic.burnDraft.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for one burn-draft turn: the cards the player takes and the cards
 * they burn from the pack they currently hold. The two lists must be disjoint,
 * may only reference cards in that pack, and must match the game's turn rules
 * (including the remainder policy on a partial pass).
 */
public record BurnTurnRequest(
    @JsonProperty("draftCardIds") @NotNull @Size(max = 25) List<@NotBlank @Size(max = 64) String> draftCardIds,
    @JsonProperty("burnCardIds") @NotNull @Size(max = 25) List<@NotBlank @Size(max = 64) String> burnCardIds
) {
}
