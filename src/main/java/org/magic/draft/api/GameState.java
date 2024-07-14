package org.magic.draft.api;

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