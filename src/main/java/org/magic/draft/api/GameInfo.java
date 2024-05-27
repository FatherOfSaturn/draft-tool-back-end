package org.magic.draft.api;

import java.util.List;
import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "gameID", "players" })
public class GameInfo {
    private final String gameID;
    private final List<Player> playerStates;

    @JsonCreator
    @BsonCreator
    public GameInfo(@JsonProperty("gameID") @BsonProperty("gameID") final String gameID,
                    @JsonProperty("players") @BsonProperty("players") final List<Player> playerStates) {
        this.gameID = Objects.requireNonNull(gameID, "gameID Required for Game Info");
        this.playerStates = Objects.requireNonNull(playerStates, "player list Required for Game Info");
    }

    public String getGameID() {
        return gameID;
    }

    public List<Player> getPlayers() {
        return playerStates;
    }
}