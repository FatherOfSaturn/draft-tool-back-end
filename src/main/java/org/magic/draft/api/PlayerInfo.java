package org.magic.draft.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PlayerInfo {
    private final String playerName;
    private final String playerID;

    @JsonCreator
    PlayerInfo(@JsonProperty("name") final String playerName,
               @JsonProperty("playerID") final String playerID) {
        this.playerName = Objects.requireNonNull(playerName, "name required for incoming Player");
        this.playerID = Objects.requireNonNull(playerID, "playerID required for player");
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPlayerID() {
        return playerID;
    }
}