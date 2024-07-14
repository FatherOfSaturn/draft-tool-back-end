package org.magic.draft.api;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "gameID", "cubeID", "numberOfDoubleDraftPicksPerPlayer" })
public class GameCreationInfo {
    private final String gameID;
    private final String cubeID;
    private final int numberOfDoubleDraftPicksPerPlayer;
    private final List<PlayerCreationInfo> playerInfo;

    @JsonCreator
    public GameCreationInfo(@JsonProperty("gameID") final String gameID,
                    @JsonProperty("cubeID") final String cubeID,
                    @JsonProperty("players") final List<PlayerCreationInfo> playerInfo,
                    @JsonProperty("numberOfDoubleDraftPicksPerPlayer") final int numberOfDoubleDraftPicksPerPlayer) {
        this.gameID = Objects.requireNonNull(gameID, "gameID Required for Game Info");
        this.cubeID = Objects.requireNonNull(cubeID, "cubeID Required for Game Info");
        this.playerInfo = Objects.requireNonNull(playerInfo, "players Required for Game Info");
        this.numberOfDoubleDraftPicksPerPlayer = Objects.requireNonNull(numberOfDoubleDraftPicksPerPlayer, "numberOfDoubleDraftPicksPerPlayer Required for GameInfo");
    }

    public String getGameID() {
        return gameID;
    }

    public int getNumberOfDoubleDraftPicksPerPlayer() {
        return numberOfDoubleDraftPicksPerPlayer;
    }

    public String getCubeID() {
        return cubeID;
    }

    public List<PlayerCreationInfo> getPlayerInfo() {
        return playerInfo;
    }
}