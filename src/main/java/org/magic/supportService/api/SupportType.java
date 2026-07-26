package org.magic.supportService.api;

/**
 * Enumerates the types of support requests that can be submitted.
 */
public enum SupportType {
    NEW_FEATURE("new_feature"),
    BUG_FIX("bug_fix"),
    MISC_SUPPORT("misc_support");

    private final String description;

    SupportType(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parses a type from its description string (case-insensitive).
     *
     * @param description the type description (e.g. "bug_fix")
     * @return the matching {@link SupportType}
     * @throws IllegalArgumentException if no matching type is found
     */
    public static SupportType fromString(final String description) {
        for (SupportType type : SupportType.values()) {
            if (type.description.equalsIgnoreCase(description)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No SupportType with description: " + description);
    }
}
