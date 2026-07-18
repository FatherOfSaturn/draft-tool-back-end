package org.magic.pyramidDraft.api;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Request payload for creating a new pyramid draft game. Contains the cube ID,
 * player information, and the number of double-draft picks allowed per player.
 */
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
