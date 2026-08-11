package org.magic.classicDraft.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@Getter
@Setter
@EqualsAndHashCode
@JsonPropertyOrder({ "gameID", "cubeID", "gameType", "players", "gameState", "createdAt", "currentPackIndex", "draftDirection" })
public class ClassicGameInfo {
    private final String gameID;
    private final String cubeID;
    private final String gameType;
    private List<ClassicPlayer> players;
    private ClassicGameState gameState;
    private final Instant createdAt;
    private int currentPackIndex;
    private DraftDirection draftDirection;

    @JsonCreator
    @BsonCreator
    public ClassicGameInfo(
            @JsonProperty("gameID") @BsonProperty("gameID") final String gameID,
            @JsonProperty("cubeID") @BsonProperty("cubeID") final String cubeID,
            @JsonProperty("gameType") @BsonProperty("gameType") final String gameType,
            @JsonProperty("players") @BsonProperty("players") final List<ClassicPlayer> players,
            @JsonProperty("gameState") @BsonProperty("gameState") final ClassicGameState gameState,
            @JsonProperty("createdAt") @BsonProperty("createdAt") final Instant createdAt,
            @JsonProperty("currentPackIndex") @BsonProperty("currentPackIndex") final int currentPackIndex,
            @JsonProperty("draftDirection") @BsonProperty("draftDirection") final DraftDirection draftDirection) {
        this.gameID = Objects.requireNonNull(gameID, "gameID Required for ClassicGameInfo");
        this.cubeID = cubeID;
        this.gameType = Objects.requireNonNullElse(gameType, "classic");
        this.players = Objects.requireNonNull(players, "players Required for ClassicGameInfo");
        this.gameState = Objects.requireNonNull(gameState, "gameState Required for ClassicGameInfo");
        this.createdAt = createdAt;
        this.currentPackIndex = currentPackIndex;
        this.draftDirection = Objects.requireNonNullElse(draftDirection, DraftDirection.ASCENDING);
    }

    public void updatePlayers(final List<ClassicPlayer> players) {
        this.players = players;
    }

    @Override
    public String toString() {
        return "ClassicGameInfo [gameID=" + gameID + ", cubeID=" + cubeID + ", gameType=" + gameType
                + ", players#=" + players.size() + ", gameState=" + gameState
                + ", currentPackIndex=" + currentPackIndex + ", draftDirection=" + draftDirection + "]";
    }
}
