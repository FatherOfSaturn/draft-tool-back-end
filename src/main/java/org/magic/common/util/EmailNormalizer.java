package org.magic.common.util;

/**
 * Normalizes an email address so that provider-specific aliases collapse to a
 * single identity. Used for per-email rate limiting on support submissions so
 * that `j.o.e+x@gmail.com`, `joe@googlemail.com`, and `JOE@gmail.com` all count
 * as the same mailbox.
 *
 * <p>Only Gmail aliases are rewritten: Gmail ignores dots in the local part and
 * supports {@code +tag} addressing, and {@code googlemail.com} is the same
 * mailbox as {@code gmail.com}. Other providers are only lowercased — stripping
 * dots or {@code +tag} from arbitrary providers would wrongly merge distinct
 * real addresses, and some providers reject such mail entirely.</p>
 */
public final class EmailNormalizer {

    private static final String GMAIL = "gmail.com";
    private static final String GOOGLEMAIL = "googlemail.com";

    private EmailNormalizer() {
    }

    /**
     * Normalizes an email address for equivalence comparisons.
     *
     * @param email the raw email address, may be {@code null} or blank
     * @return the normalized address, or the lowercased input when it is not a
     *         parseable address; {@code null} when the input is {@code null}
     */
    public static String normalize(final String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase();
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return trimmed;
        }
        String localPart = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);
        if (GMAIL.equals(domain) || GOOGLEMAIL.equals(domain)) {
            int plus = localPart.indexOf('+');
            if (plus >= 0) {
                localPart = localPart.substring(0, plus);
            }
            localPart = localPart.replace(".", "");
            domain = GMAIL;
        }
        return localPart + "@" + domain;
    }
}
