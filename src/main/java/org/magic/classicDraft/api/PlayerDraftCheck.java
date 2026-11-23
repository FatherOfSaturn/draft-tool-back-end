package org.magic.classicDraft.api;

import org.magic.common.game.GameState;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "canDraft", "gameState" })
public record PlayerDraftCheck(
    @JsonProperty("canDraft") boolean canDraft,
    @JsonProperty("gameState") GameState gameState
) {}
