package org.magic.lobbyService.api;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Enumerates the lifecycle statuses of a lobby:
 * {@link #WAITING} → {@link #STARTING} → {@link #STARTED}.
 */
public enum LobbyStatus {
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
        for (LobbyStatus status : LobbyStatus.values()) {
            if (status.description.equalsIgnoreCase(description)) {
                return status;
            }
        }
        throw new IllegalArgumentException("No LobbyStatus with description: " + description);
    }
}
