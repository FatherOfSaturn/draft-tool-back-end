package org.magic.accountService.app;

import java.util.Set;
import java.util.UUID;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Issues signed application JWTs after a successful Google login. The tokens
 * are signed with the symmetric key configured via {@code smallrye.jwt.sign.key}
 * (an {@code oct} JWK whose {@code k} is the base64url-encoded {@code APP_JWT_SECRET})
 * and verified on every request by Quarkus SmallRye JWT.
 */
@ApplicationScoped
public class JwtService {

    private static final long TTL_SECONDS = 86_400;

    /**
     * Builds a signed JWT for the given subject (the Google {@code sub}).
     *
     * @param subject the authenticated user's Google subject
     * @param email   the user's email
     * @param groups  the roles to embed (e.g. "user", "admin")
     * @return the compact, signed JWT
     */
    public String issueToken(final String subject, final String email, final Set<String> groups) {
        return Jwt.issuer("magic-draft")
                .subject(subject)
                .claim("email", email)
                .groups(groups)
                .claim("jti", UUID.randomUUID().toString())
                .expiresAt(System.currentTimeMillis() + TTL_SECONDS * 1000)
                .sign();
    }

    public long getTokenTtlSeconds() {
        return TTL_SECONDS;
    }
}
