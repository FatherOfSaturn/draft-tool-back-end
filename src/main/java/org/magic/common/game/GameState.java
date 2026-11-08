package org.magic.common.game;

import org.magic.common.util.EnumParser;

/**
 * Enumerates the lifecycle states of a pyramid draft game:
 * {@link #GAME_STARTED} → {@link #GAME_MERGED} → {@link #GAME_COMPLETE}.
 */
public enum GameState implements EnumParser.Described {
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

    public static GameState fromString(final String description) {
        return EnumParser.parse(GameState.class, description);
    }
}