package org.magic.classicDraft.api;

import org.magic.common.util.EnumParser;

public enum ClassicGameState implements EnumParser.Described {
    GAME_STARTED("game_started"),
    GAME_COMPLETE("game_complete");

    private final String description;

    ClassicGameState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static ClassicGameState fromString(final String description) {
        return EnumParser.parse(ClassicGameState.class, description);
    }
}
