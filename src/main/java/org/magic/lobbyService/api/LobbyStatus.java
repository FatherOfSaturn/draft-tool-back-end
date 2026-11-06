package org.magic.lobbyService.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.magic.common.util.EnumParser;

/**
 * Enumerates the lifecycle statuses of a lobby:
 * {@link #WAITING} → {@link #STARTING} → {@link #STARTED}.
 */
public enum LobbyStatus implements EnumParser.Described {
    WAITING("waiting"),
    STARTING("starting"),
    STARTED("started");

    private final String description;

    LobbyStatus(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parses a status from its description string (case-insensitive).
     *
     * @param description the status description (e.g. "waiting")
     * @return the matching {@link LobbyStatus}
     * @throws IllegalArgumentException if no matching status is found
     */
    @JsonCreator
    public static LobbyStatus fromString(final String description) {
        return EnumParser.parse(LobbyStatus.class, description);
    }
}
