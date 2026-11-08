package org.magic.burnDraft.api;

import java.time.Instant;
import java.util.Objects;

import org.magic.common.game.GameState;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Per-player summary row of a burn draft game, used by the player history
 * endpoint (mirrors {@code ClassicGameSummary} plus the burned-card count).
 */
@JsonPropertyOrder({ "gameID", "cubeID", "gameState", "playerName", "draftOrderNumber",
                     "cardsDraftedCount", "cardsBurnedCount", "totalPacks", "currentPackIndex", "createdAt" })
public record BurnGameSummary(
    @JsonProperty("gameID") String gameID,
    @JsonProperty("cubeID") String cubeID,
    @JsonProperty("gameState") GameState gameState,
    @JsonProperty("playerName") String playerName,
    @JsonProperty("draftOrderNumber") int draftOrderNumber,
    @JsonProperty("cardsDraftedCount") int cardsDraftedCount,
    @JsonProperty("cardsBurnedCount") int cardsBurnedCount,
    @JsonProperty("totalPacks") int totalPacks,
    @JsonProperty("currentPackIndex") int currentPackIndex,
    @JsonProperty("createdAt") Instant createdAt
) {
    public BurnGameSummary {
        Objects.requireNonNull(gameID, "gameID Required for BurnGameSummary");
        Objects.requireNonNull(gameState, "gameState Required for BurnGameSummary");
    }
}
