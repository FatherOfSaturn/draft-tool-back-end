package org.magic.classicDraft.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "gameID", "gameState", "draftDirection", "player" })
public record PlayerDraftData(
    @JsonProperty("gameID") String gameID,
    @JsonProperty("gameState") ClassicGameState gameState,
    @JsonProperty("draftDirection") DraftDirection draftDirection,
    @JsonProperty("player") DraftPlayerSnapshot player
) {}
