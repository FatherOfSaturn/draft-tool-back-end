package org.magic.accountService.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Unified game history entry aggregating games of any draft type into a
 * single shape for the account history endpoint.
 */
public record GameHistoryEntry(
    @JsonProperty("gameID") String gameID,
    @JsonProperty("cubeID") String cubeID,
    @JsonProperty("gameType") String gameType,
    @JsonProperty("gameState") DraftGameState gameState,
    @JsonProperty("players") List<GameHistoryPlayer> players,
    @JsonProperty("createdAt") Instant createdAt
) {
}
