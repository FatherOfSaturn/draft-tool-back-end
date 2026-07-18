package org.magic.pyramidDraft.api;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Lightweight summary of a game for history/listing purposes.
 * Contains only the metadata needed to display past games and to
 * re-fetch the full game data via {@code gameID} if needed.
 */
@JsonPropertyOrder({ "gameID", "cubeID", "gameState", "player1Name", "player2Name", "createdAt" })
public record GameSummary(
    @JsonProperty("gameID") String gameID,
    @JsonProperty("cubeID") String cubeID,
    @JsonProperty("gameState") GameState gameState,
    @JsonProperty("player1Name") String player1Name,
    @JsonProperty("player2Name") String player2Name,
    @JsonProperty("createdAt") Instant createdAt
) {

    public GameSummary {
        Objects.requireNonNull(gameID, "gameID Required for Game Summary");
        Objects.requireNonNull(gameState, "gameState Required for Game Summary");
    }
}
