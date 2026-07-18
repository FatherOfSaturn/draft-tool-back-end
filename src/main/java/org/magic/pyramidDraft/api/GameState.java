package org.magic.pyramidDraft.api;

/**
 * Enumerates the lifecycle states of a pyramid draft game:
 * {@link #GAME_STARTED} → {@link #GAME_MERGED} → {@link #GAME_COMPLETE}.
 */
public enum GameState {
    GAME_STARTED("game_started"),
    GAME_MERGED("game_merged"),
    GAME_COMPLETE("game_complete");

    private final String description;

    GameState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static GameState fromString(String description) {
        for (GameState state : GameState.values()) {
            if (state.description.equalsIgnoreCase(description)) {
                return state;
            }
        }
        throw new IllegalArgumentException("No enum constant with description " + description);
    }
}