package org.magic.supportService.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.magic.common.util.EnumParser;

/**
 * Enumerates the types of support requests that can be submitted.
 */
public enum SupportType implements EnumParser.Described {
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
    @JsonCreator
    public static SupportType fromString(final String description) {
        return EnumParser.parse(SupportType.class, description);
    }
}
