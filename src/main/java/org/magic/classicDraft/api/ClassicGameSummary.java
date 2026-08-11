package org.magic.classicDraft.api;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "gameID", "cubeID", "gameState", "playerName", "draftOrderNumber", "cardsDraftedCount",
                     "totalPacks", "currentPackIndex", "createdAt" })
public record ClassicGameSummary(
    @JsonProperty("gameID") String gameID,
    @JsonProperty("cubeID") String cubeID,
    @JsonProperty("gameState") ClassicGameState gameState,
    @JsonProperty("playerName") String playerName,
    @JsonProperty("draftOrderNumber") int draftOrderNumber,
    @JsonProperty("cardsDraftedCount") int cardsDraftedCount,
    @JsonProperty("totalPacks") int totalPacks,
    @JsonProperty("currentPackIndex") int currentPackIndex,
    @JsonProperty("createdAt") Instant createdAt
) {
    public ClassicGameSummary {
        Objects.requireNonNull(gameID, "gameID Required for ClassicGameSummary");
        Objects.requireNonNull(gameState, "gameState Required for ClassicGameSummary");
    }
}
