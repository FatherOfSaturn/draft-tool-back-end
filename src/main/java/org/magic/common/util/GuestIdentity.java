package org.magic.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Produces the stable, non-reversible identity marker used for anonymous
 * (guest) players and hosts. The raw player token is never stored or
 * serialized; only this SHA-256-derived marker is persisted.
 */
public final class GuestIdentity {

    private static final String PREFIX = "guest-";

    /**
     * Hashes a player token into the stable {@code guest-<sha256-hex>} marker.
     *
     * @param playerToken the raw token issued on join
     * @return the non-reversible guest identity marker
     */
    public static String id(final String playerToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(playerToken.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private GuestIdentity() {
    }
}
