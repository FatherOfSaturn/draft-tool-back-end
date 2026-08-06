package org.magic.accountService.api;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Draft-format-agnostic game lifecycle state, shared across all game types
 * for the unified game history endpoint. Serialized to lowercase snake_case
 * so clients don't depend on per-format enum names.
 */
public enum DraftGameState {
    GAME_CREATED("game_created"),
    GAME_IN_PROGRESS("game_in_progress"),
    GAME_MERGED("game_merged"),
    GAME_COMPLETE("game_complete");

    private final String description;

    DraftGameState(final String description) {
        this.description = description;
    }

    @JsonValue
    public String getDescription() {
        return description;
    }
}
