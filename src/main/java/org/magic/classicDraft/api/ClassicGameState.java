package org.magic.classicDraft.api;

public enum ClassicGameState {
    GAME_STARTED("game_started"),
    GAME_COMPLETE("game_complete");

    private final String description;

    ClassicGameState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static ClassicGameState fromString(String description) {
        for (ClassicGameState state : ClassicGameState.values()) {
            if (state.description.equalsIgnoreCase(description)) {
                return state;
            }
        }
        throw new IllegalArgumentException("No enum constant with description " + description);
    }
}
