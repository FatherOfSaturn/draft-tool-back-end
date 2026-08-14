package org.magic.lobbyService.api;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
    @JsonProperty("draftType") @NotBlank @Size(max = 30) String draftType,
    @JsonProperty("config") @NotNull @Size(max = 50) Map<String, Object> config,
    @JsonProperty("hostAccountID") @NotBlank @Size(max = 100) String hostAccountID,
    @JsonProperty("hostDisplayName")
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "hostDisplayName must not contain control characters")
    String hostDisplayName,
    @JsonProperty("minPlayers") @Min(2) @Max(12) Integer minPlayers,
    @JsonProperty("maxPlayers") @Min(2) @Max(12) Integer maxPlayers
) {

    public CreateLobbyRequest {
        Objects.requireNonNull(draftType, "draftType is required for CreateLobbyRequest");
        Objects.requireNonNull(config, "config is required for CreateLobbyRequest");
        Objects.requireNonNull(hostAccountID, "hostAccountID is required for CreateLobbyRequest");
        Objects.requireNonNull(hostDisplayName, "hostDisplayName is required for CreateLobbyRequest");
    }
}
