package org.magic.burnDraft.api;

import org.magic.common.game.GameState;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Whether the identified player can currently take a turn in a burn draft.
 */
@JsonPropertyOrder({ "canDraft", "gameState" })
public record PlayerBurnCheck(
    @JsonProperty("canDraft") boolean canDraft,
    @JsonProperty("gameState") GameState gameState
) {
}
