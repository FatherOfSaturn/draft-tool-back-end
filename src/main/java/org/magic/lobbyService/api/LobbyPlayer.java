package org.magic.lobbyService.api;

import java.time.Instant;

import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A participant in a lobby session.
 * Each player is assigned a unique {@code playerToken} on join to allow
 * anonymous (unauthenticated) participants to identify themselves for polling and leaving.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({ "accountID", "displayName", "slotIndex", "playerToken", "lastPollAt" })
public class LobbyPlayer {

    @BsonProperty("accountID")
    @JsonProperty("accountID")
    private String accountID;

    @BsonProperty("displayName")
    @JsonProperty("displayName")
    private String displayName;

    @BsonProperty("slotIndex")
    @JsonProperty("slotIndex")
    private int slotIndex;

    @BsonProperty("playerToken")
    @JsonProperty("playerToken")
    private String playerToken;

    @BsonProperty("lastPollAt")
    @JsonProperty("lastPollAt")
    private Instant lastPollAt;
}
