package org.magic.pyramidDraft.api;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "gameID", "cubeID", "numberOfDoubleDraftPicksPerPlayer" })
public record GameCreationInfo(
    @JsonProperty("gameID") String gameID,
    @JsonProperty("cubeID") String cubeID,
    @JsonProperty("players") List<PlayerCreationInfo> playerInfo,
    @JsonProperty("numberOfDoubleDraftPicksPerPlayer") int numberOfDoubleDraftPicksPerPlayer
) {

    public GameCreationInfo {
        Objects.requireNonNull(gameID, "gameID Required for Game Info");
        Objects.requireNonNull(cubeID, "cubeID Required for Game Info");
        Objects.requireNonNull(playerInfo, "players Required for Game Info");
    }
}
