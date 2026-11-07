package org.magic.common.game;

import org.magic.common.util.EnumParser;

public enum DraftDirection implements EnumParser.Described {
    ASCENDING("ascending"),
    DESCENDING("descending");

    private final String description;

    DraftDirection(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static DraftDirection fromString(final String description) {
        return EnumParser.parse(DraftDirection.class, description);
    }
}
