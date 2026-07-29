package org.magic.pyramidDraft.api;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Lightweight summary of a game for history/listing purposes.
 * Contains metadata and per-player progress needed to display
 * past games and determine what action the user can take.
 */
@JsonPropertyOrder({ "gameID", "cubeID", "gameState",
                     "player1Name", "player1CurrentPack", "player1TotalPacks", "player1DoneDrafting",
                     "player2Name", "player2CurrentPack", "player2TotalPacks", "player2DoneDrafting",
                     "createdAt" })
public record GameSummary(
    @JsonProperty("gameID") String gameID,
    @JsonProperty("cubeID") String cubeID,
    @JsonProperty("gameState") GameState gameState,
    @JsonProperty("player1Name") String player1Name,
    @JsonProperty("player1CurrentPack") int player1CurrentPack,
    @JsonProperty("player1TotalPacks") int player1TotalPacks,
    @JsonProperty("player1DoneDrafting") boolean player1DoneDrafting,
    @JsonProperty("player2Name") String player2Name,
    @JsonProperty("player2CurrentPack") int player2CurrentPack,
    @JsonProperty("player2TotalPacks") int player2TotalPacks,
    @JsonProperty("player2DoneDrafting") boolean player2DoneDrafting,
    @JsonProperty("createdAt") Instant createdAt
) {

    public GameSummary {
        Objects.requireNonNull(gameID, "gameID Required for Game Summary");
        Objects.requireNonNull(gameState, "gameState Required for Game Summary");
    }
}
