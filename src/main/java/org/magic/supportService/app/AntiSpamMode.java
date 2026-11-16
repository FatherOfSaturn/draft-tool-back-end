package org.magic.supportService.app;

import org.magic.common.util.EnumParser;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Rollout switch for the support-form anti-spam checks. Read from the
 * {@code support.anti-spam.mode} config property.
 *
 * <ul>
 *   <li>{@link #OFF} — all token/Turnstile checks skipped (honeypot and rate
 *       limiting still apply).</li>
 *   <li>{@link #REPORT_ONLY} — checks run and are logged/counted, but never
 *       reject; used to validate the checks against real traffic.</li>
 *   <li>{@link #TOKEN_ONLY} — the signed form token is enforced; Turnstile is
 *       not.</li>
 *   <li>{@link #FULL} — form token and Turnstile are both enforced.</li>
 * </ul>
 */
public enum AntiSpamMode implements EnumParser.Described {
    OFF("off"),
    REPORT_ONLY("report-only"),
    TOKEN_ONLY("token-only"),
    FULL("full");

    private final String description;

    AntiSpamMode(final String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public boolean enforcesFormToken() {
        return this == TOKEN_ONLY || this == FULL;
    }

    public boolean runsTurnstile() {
        return this == REPORT_ONLY || this == FULL;
    }

    /**
     * Parses a mode from its description string (case-insensitive).
     *
     * @param description the configured mode (e.g. "report-only")
     * @return the matching {@link AntiSpamMode}
     * @throws IllegalArgumentException if no matching mode is found
     */
    @JsonCreator
    public static AntiSpamMode fromString(final String description) {
        return EnumParser.parse(AntiSpamMode.class, description);
    }
}
