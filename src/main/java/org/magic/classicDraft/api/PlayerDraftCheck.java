package org.magic.classicDraft.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "canDraft", "gameState" })
public record PlayerDraftCheck(
    @JsonProperty("canDraft") boolean canDraft,
    @JsonProperty("gameState") ClassicGameState gameState
) {}
