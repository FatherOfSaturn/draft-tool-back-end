package org.magic.supportService.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.magic.common.util.EnumParser;

/**
 * Enumerates the lifecycle statuses of a support request:
 * {@link #NEW} → {@link #IN_PROGRESS} → {@link #BLOCKED}/{@link #COMPLETED} → {@link #DELETED}.
 * {@link #QUARANTINED} marks submissions flagged as probable spam: they are
 * stored for admin review but hidden from the public queue. Any status can be
 * transitioned to from any other status.
 */
public enum SupportStatus implements EnumParser.Described {
    NEW("new"),
    IN_PROGRESS("in_progress"),
    BLOCKED("blocked"),
    COMPLETED("completed"),
    QUARANTINED("quarantined"),
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
    @JsonCreator
    public static SupportStatus fromString(final String description) {
        return EnumParser.parse(SupportStatus.class, description);
    }
}
