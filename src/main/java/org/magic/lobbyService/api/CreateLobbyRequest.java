package org.magic.lobbyService.api;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for creating a new lobby.
 *
 * @param draftType       the draft format (e.g. "pyramid", "classic")
 * @param config          opaque configuration key-value pairs (contents vary by draft type)
 * @param hostAccountID   the account ID of the lobby host
 * @param hostDisplayName the display name of the host
 * @param minPlayers      optional override for the minimum number of players; defaults per draft type
 * @param maxPlayers      optional override for the maximum number of players; defaults per draft type
 */
public record CreateLobbyRequest(
    @JsonProperty("draftType") String draftType,
    @JsonProperty("config") Map<String, Object> config,
    @JsonProperty("hostAccountID") String hostAccountID,
    @JsonProperty("hostDisplayName") String hostDisplayName,
    @JsonProperty("minPlayers") Integer minPlayers,
    @JsonProperty("maxPlayers") Integer maxPlayers
) {

    public CreateLobbyRequest {
        Objects.requireNonNull(draftType, "draftType is required for CreateLobbyRequest");
        Objects.requireNonNull(config, "config is required for CreateLobbyRequest");
        Objects.requireNonNull(hostAccountID, "hostAccountID is required for CreateLobbyRequest");
        Objects.requireNonNull(hostDisplayName, "hostDisplayName is required for CreateLobbyRequest");
    }
}
