package org.magic.lobbyService.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A format-agnostic lobby session that stores game configuration as opaque
 * key-value pairs. Manages the player lifecycle and delegates final game
 * creation to the existing game service.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({ "lobbyCode", "draftType", "config", "hostAccountID", "players",
                     "minPlayers", "maxPlayers", "gameID", "status", "createdAt" })
public class Lobby {

    @BsonProperty("_id")
    @JsonProperty("lobbyCode")
    private String lobbyCode;

    @BsonProperty("draftType")
    @JsonProperty("draftType")
    private String draftType;

    @BsonProperty("config")
    @JsonProperty("config")
    private Map<String, Object> config;

    @BsonProperty("hostAccountID")
    @JsonProperty("hostAccountID")
    private String hostAccountID;

    @BsonProperty("players")
    @JsonProperty("players")
    private List<LobbyPlayer> players;

    @BsonProperty("minPlayers")
    @JsonProperty("minPlayers")
    private int minPlayers;

    @BsonProperty("maxPlayers")
    @JsonProperty("maxPlayers")
    private int maxPlayers;

    @BsonProperty("gameID")
    @JsonProperty("gameID")
    private String gameID;

    @BsonProperty("status")
    @JsonProperty("status")
    private LobbyStatus status;

    @BsonProperty("createdAt")
    @JsonProperty("createdAt")
    private Instant createdAt;

    @BsonProperty("hostAloneSince")
    @JsonProperty("hostAloneSince")
    private Instant hostAloneSince;
}
