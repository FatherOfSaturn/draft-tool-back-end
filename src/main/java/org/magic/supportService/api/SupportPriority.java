package org.magic.supportService.api;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Enumerates the priority levels for support requests,
 * from lowest ({@link #LOW}) to highest ({@link #CRITICAL}).
 */
public enum SupportPriority {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    private final String description;

    SupportPriority(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parses a priority from its description string (case-insensitive).
     *
     * @param description the priority description (e.g. "medium")
     * @return the matching {@link SupportPriority}
     * @throws IllegalArgumentException if no matching priority is found
     */
    @JsonCreator
    public static SupportPriority fromString(final String description) {
        for (SupportPriority priority : SupportPriority.values()) {
            if (priority.description.equalsIgnoreCase(description)) {
                return priority;
            }
        }
        throw new IllegalArgumentException("No SupportPriority with description: " + description);
    }
}
