package org.magic.supportService.app;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.common.api.ErrorMessage;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Issues and verifies stateless HMAC-signed form tokens used to gate
 * {@code POST /support} submissions.
 *
 * <p>A token is {@code <issuedAtMillis>.<base64url(HMAC-SHA256(secret, issuedAtMillis))>}.
 * On submit the signature is verified (constant-time) and the token's age must
 * fall within a configured window: tokens younger than {@code min-age} are
 * rejected (scripted instant submits) as are tokens older than {@code max-age}
 * (stolen or stale tokens). Nothing is stored server-side.</p>
 *
 * <p>The signing secret is read from {@code support.form-token.secret}
 * (environment variable {@code SUPPORT_FORM_TOKEN_SECRET}). A blank secret
 * fails closed: issuing and verifying both return a server error, so an
 * unconfigured deployment can never silently accept unsigned tokens.</p>
 */
@ApplicationScoped
@RegisterForReflection
public class FormTokenService {

    private static final Logger LOGGER = LogManager.getLogger(FormTokenService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_LENGTH = 16;

    private final byte[] secret;
    private final Duration minAge;
    private final Duration maxAge;

    @Inject
    public FormTokenService(
            @ConfigProperty(name = "support.form-token.secret") final Optional<String> secret,
            @ConfigProperty(name = "support.form-token.min-age", defaultValue = "PT3S") final Duration minAge,
            @ConfigProperty(name = "support.form-token.max-age", defaultValue = "PT2H") final Duration maxAge) {
        this.secret = secret.map(value -> value.getBytes(StandardCharsets.UTF_8)).orElseGet(() -> new byte[0]);
        this.minAge = minAge;
        this.maxAge = maxAge;
        if (isConfigured() && this.secret.length < MIN_SECRET_LENGTH) {
            LOGGER.warn("support.form-token.secret is shorter than {} characters; use a long random value",
                    MIN_SECRET_LENGTH);
        }
    }

    /**
     * Issues a fresh form token embedding the current time.
     *
     * @return the signed token
     * @throws WebApplicationException 500 when the signing secret is not configured
     */
    public String issue() {
        requireConfiguredSecret();
        long issuedAt = System.currentTimeMillis();
        String payload = Long.toString(issuedAt);
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));
    }

    /**
     * Verifies a submitted form token: signature must match (constant-time
     * comparison) and the token must be neither too fresh nor too old.
     *
     * @param token the submitted token, may be {@code null} or blank
     * @throws WebApplicationException 400 for missing/invalid/expired/too-fast
     *         tokens; 500 when the signing secret is not configured
     */
    public void verify(final String token) {
        requireConfiguredSecret();
        if (token == null || token.isBlank()) {
            throw badRequest("Missing form token. Please reload the form and try again.");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw badRequest("Invalid form token. Please reload the form and try again.");
        }
        byte[] provided;
        try {
            provided = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw badRequest("Invalid form token. Please reload the form and try again.");
        }
        if (!MessageDigest.isEqual(hmac(parts[0]), provided)) {
            throw badRequest("Invalid form token. Please reload the form and try again.");
        }

        long issuedAt;
        try {
            issuedAt = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw badRequest("Invalid form token. Please reload the form and try again.");
        }
        long now = System.currentTimeMillis();
        long age = now - issuedAt;
        if (issuedAt > now) {
            throw badRequest("Invalid form token. Please reload the form and try again.");
        }
        if (age < minAge.toMillis()) {
            LOGGER.info("Rejected support submission: form token used {}ms after issue", age);
            throw badRequest("Form submitted too quickly. Please try again.");
        }
        if (age > maxAge.toMillis()) {
            throw badRequest("Form session expired. Please reload the form and try again.");
        }
    }

    private void requireConfiguredSecret() {
        if (!isConfigured()) {
            LOGGER.error("support.form-token.secret is not configured; refusing to issue/verify form tokens");
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorMessage("Support form verification is misconfigured."))
                    .build());
        }
    }

    private boolean isConfigured() {
        return secret != null && secret.length > 0;
    }

    byte[] hmac(final String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            LOGGER.error("HMAC-SHA256 unavailable", e);
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private WebApplicationException badRequest(final String message) {
        return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorMessage(message))
                .build());
    }
}
