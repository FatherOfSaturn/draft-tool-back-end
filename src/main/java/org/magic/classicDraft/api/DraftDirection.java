package org.magic.classicDraft.api;

public enum DraftDirection {
    ASCENDING("ascending"),
    DESCENDING("descending");

    private final String description;

    DraftDirection(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static DraftDirection fromString(String description) {
        for (DraftDirection dir : DraftDirection.values()) {
            if (dir.description.equalsIgnoreCase(description)) {
                return dir;
            }
        }
        throw new IllegalArgumentException("No enum constant with description " + description);
    }
}
