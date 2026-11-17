package org.magic.burnDraft.api;

import org.magic.common.util.EnumParser;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Decides how the final, non-divisible pass of a burn-draft pack is handled.
 *
 * <p>A turn normally drafts {@code cardsDraftedPerPack} cards and burns
 * {@code cardsBurnedPerPack} cards. When a pack holds fewer cards than that
 * total (a partial pass), the leftover cards beyond the draft quota are either
 * burned ({@link #BURN_EXTRAS}) or drafted as well ({@link #DRAFT_EXTRAS}).
 * The policy is chosen when the game is created.</p>
 */
public enum BurnRemainderPolicy implements EnumParser.Described {
    BURN_EXTRAS("burn_extras"),
    DRAFT_EXTRAS("draft_extras");

    private final String description;

    BurnRemainderPolicy(final String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @JsonCreator
    public static BurnRemainderPolicy fromString(final String description) {
        return EnumParser.parse(BurnRemainderPolicy.class, description);
    }
}
