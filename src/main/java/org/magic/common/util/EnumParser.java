package org.magic.common.util;

/**
 * Shared lookup helper for description-based enums. Every wire-facing enum in
 * the API (game states, lobby status, support enums, draft direction) parses
 * its {@code @JsonCreator} values from a human-readable description string,
 * case-insensitively; this centralizes that loop and the error contract.
 */
public final class EnumParser {

    /**
     * An enum constant that carries a human-readable description used for
     * JSON serialization and parsing.
     */
    public interface Described {
        String getDescription();
    }

    /**
     * Parses a description string (case-insensitive) into the matching enum constant.
     *
     * @param type        the enum class
     * @param description the description to look up (e.g. {@code "in_progress"})
     * @param <E>         the enum type
     * @return the matching constant
     * @throws IllegalArgumentException if no constant's description matches
     */
    public static <E extends Enum<E> & Described> E parse(final Class<E> type, final String description) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getDescription().equalsIgnoreCase(description)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("No " + type.getSimpleName() + " with description: " + description);
    }

    private EnumParser() {
    }
}
