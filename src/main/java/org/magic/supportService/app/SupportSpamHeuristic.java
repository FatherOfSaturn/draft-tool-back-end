package org.magic.supportService.app;

/**
 * Heuristic spam detector for support-request descriptions. Instead of
 * dropping suspicious submissions, callers flag them as
 * {@link org.magic.supportService.api.SupportStatus#QUARANTINED} so an admin
 * can review them.
 *
 * <p>The heuristics are deliberately conservative — real users legitimately
 * paste stack traces, card names, and long identifiers — and only apply to
 * sufficiently long descriptions:</p>
 * <ul>
 *   <li>a single whitespace-free run longer than {@value #MAX_RUN} characters,</li>
 *   <li>or a very low vowel ratio among the letters, typical of gibberish.</li>
 * </ul>
 */
public final class SupportSpamHeuristic {

    /** Minimum description length before heuristics are applied. */
    static final int MIN_LENGTH = 80;
    /** Longest whitespace-free run tolerated (identifiers in stack traces can
     * legitimately exceed 40 characters). */
    static final int MAX_RUN = 60;
    /** Lowest tolerated ratio of vowels to letters. */
    static final double MIN_VOWEL_RATIO = 0.2;

    private static final String VOWELS = "aeiou";

    private SupportSpamHeuristic() {
    }

    /**
     * Evaluates whether a description looks like spam.
     *
     * @param description the submitted description, may be {@code null}
     * @return {@code true} when the description is long enough and matches a
     *         spam heuristic
     */
    public static boolean isLikelySpam(final String description) {
        if (description == null || description.length() < MIN_LENGTH) {
            return false;
        }

        String[] runs = description.split("\\s+");
        for (String run : runs) {
            if (run.length() > MAX_RUN) {
                return true;
            }
        }

        int letters = 0;
        int vowels = 0;
        for (int i = 0; i < description.length(); i++) {
            char c = Character.toLowerCase(description.charAt(i));
            if (c >= 'a' && c <= 'z') {
                letters++;
                if (VOWELS.indexOf(c) >= 0) {
                    vowels++;
                }
            }
        }
        return letters > 0 && (double) vowels / letters < MIN_VOWEL_RATIO;
    }

}
