package org.magic.supportService.api;

/**
 * Enumerates the lifecycle statuses of a support request:
 * {@link #NEW} → {@link #IN_PROGRESS} → {@link #BLOCKED}/{@link #COMPLETED} → {@link #DELETED}.
 * Any status can be transitioned to from any other status.
 */
public enum SupportStatus {
    NEW("new"),
    IN_PROGRESS("in_progress"),
    BLOCKED("blocked"),
    COMPLETED("completed"),
    DELETED("deleted");

    private final String description;

    SupportStatus(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parses a status from its description string (case-insensitive).
     *
     * @param description the status description (e.g. "in_progress")
     * @return the matching {@link SupportStatus}
     * @throws IllegalArgumentException if no matching status is found
     */
    public static SupportStatus fromString(final String description) {
        for (SupportStatus status : SupportStatus.values()) {
            if (status.description.equalsIgnoreCase(description)) {
                return status;
            }
        }
        throw new IllegalArgumentException("No SupportStatus with description: " + description);
    }
}
