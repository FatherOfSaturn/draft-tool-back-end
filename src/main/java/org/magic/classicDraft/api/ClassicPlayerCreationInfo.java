package org.magic.classicDraft.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClassicPlayerCreationInfo(
    @JsonProperty("name") String playerName,
    @JsonProperty("accountID") String accountID
) {
    public ClassicPlayerCreationInfo {
        Objects.requireNonNull(playerName, "name required for ClassicPlayerCreationInfo");
    }
}
