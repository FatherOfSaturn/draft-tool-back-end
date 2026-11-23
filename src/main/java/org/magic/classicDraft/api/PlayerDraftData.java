package org.magic.classicDraft.api;

import org.magic.common.game.DraftDirection;

import org.magic.common.game.GameState;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "gameID", "gameState", "draftDirection", "player" })
public record PlayerDraftData(
    @JsonProperty("gameID") String gameID,
    @JsonProperty("gameState") GameState gameState,
    @JsonProperty("draftDirection") DraftDirection draftDirection,
    @JsonProperty("player") DraftPlayerSnapshot player
) {}
