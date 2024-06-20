package org.magic.draft.api;

public enum GameState {
    GAME_STARTED("Game has started"),
    GAME_MERGED("Game has merged"),
    GAME_COMPLETE("Game is complete");

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