package org.magic.supportService.external;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.common.api.ErrorMessage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

/**
 * Verifies Cloudflare Turnstile tokens against the Siteverify API
 * ({@code POST https://challenges.cloudflare.com/turnstile/v0/siteverify}).
 *
 * <p>Cloudflare's documentation makes server-side validation mandatory: a
 * widget token could be invalid, expired (300 s), or already redeemed, so a
 * missing or failing token must never reach persistence. The secret key is
 * read from {@code turnstile.secret-key} (environment variable
 * {@code TURNSTILE_SECRET_KEY}), never from code.</p>
 *
 * <p>The check <strong>fails closed</strong>: an unreachable siteverify
 * endpoint, a non-2xx reply, or an unparseable body all reject the submission
 * with 400. When {@code turnstile.enabled=false} the verification is skipped
 * entirely (dev/test mode).</p>
 *
 * @param remoteIp the resolved client IP, forwarded to Cloudflare as
 *        {@code remoteip} when available
 */
@ApplicationScoped
public class TurnstileVerifier {

    private static final Logger LOGGER = LogManager.getLogger(TurnstileVerifier.class);
    private static final String DEFAULT_SITEVERIFY_URL =
            "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final int MAX_TOKEN_LENGTH = 2048;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final boolean enabled;
    private final String secret;
    private final String siteverifyUrl;
    private final Set<String> allowedHostnames;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ManagedExecutor managedExecutor;

    @Inject
    public TurnstileVerifier(
            @ConfigProperty(name = "turnstile.enabled", defaultValue = "false") final boolean enabled,
            @ConfigProperty(name = "turnstile.secret-key") final Optional<String> secret,
            @ConfigProperty(name = "turnstile.siteverify-url", defaultValue = DEFAULT_SITEVERIFY_URL) final String siteverifyUrl,
            @ConfigProperty(name = "turnstile.allowed-hostnames") final Optional<String> allowedHostnames,
            final ManagedExecutor managedExecutor) {
        this.enabled = enabled;
        this.secret = secret.orElse("");
        this.siteverifyUrl = siteverifyUrl;
        this.allowedHostnames = allowedHostnames
                .map(value -> (Set<String>) Set.copyOf(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .filter(host -> !host.isEmpty())
                        .toList()))
                .orElseGet(Set::of);
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.managedExecutor = managedExecutor;
        if (enabled && this.allowedHostnames.isEmpty()) {
            LOGGER.warn("turnstile.enabled is true but turnstile.allowed-hostnames is empty; "
                    + "siteverify hostname will not be checked");
        }
    }

    /**
     * Whether Turnstile verification is active at all.
     *
     * @return {@code true} when the verifier is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether a site secret is configured. Used by deployment-configuration
     * tests and the startup log; never exposes the value.
     *
     * @return {@code true} when a non-blank secret is present
     */
    public boolean isSecretConfigured() {
        return secret != null && !secret.isBlank();
    }

    /**
     * The allowed hostnames, lowercased. Exposed for deployment-configuration
     * tests.
     *
     * @return an immutable set (empty when no allow-list is configured)
     */
    public Set<String> allowedHostnames() {
        return allowedHostnames;
    }

    /**
     * Verifies the client's Turnstile token.
     *
     * @param token the {@code cf-turnstile-response} value submitted by the client
     * @param remoteIp the client IP, or {@code null}/blank when unknown
     * @return a {@link Uni} completing when the token is verified
     * @throws WebApplicationException (wrapped in the {@link Uni}) with 400 when
     *         the token is missing/invalid or siteverify could not confirm it;
     *         500 when the secret key is not configured
     */
    public Uni<Void> verify(final String token, final String remoteIp) {
        if (!enabled) {
            return Uni.createFrom().voidItem();
        }
        if (token == null || token.isBlank()) {
            return Uni.createFrom().failure(badRequest(
                    "Missing CAPTCHA verification. Please complete the verification and try again."));
        }
        if (token.length() > MAX_TOKEN_LENGTH) {
            return Uni.createFrom().failure(badRequest("Invalid CAPTCHA verification."));
        }
        if (secret == null || secret.isBlank()) {
            LOGGER.error("turnstile.secret-key is not configured; rejecting submission");
            return Uni.createFrom().failure(new WebApplicationException(Response
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorMessage("Support form verification is misconfigured."))
                    .build()));
        }

        StringBuilder body = new StringBuilder();
        body.append("secret=").append(urlEncode(secret));
        body.append("&response=").append(urlEncode(token));
        if (remoteIp != null && !remoteIp.isBlank()) {
            body.append("&remoteip=").append(urlEncode(remoteIp));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(siteverifyUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        return Uni.createFrom().completionStage(() -> httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .runSubscriptionOn(managedExecutor)
                .onItem().transform(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.error("Turnstile siteverify returned HTTP {}: rejecting submission",
                                response.statusCode());
                        throw badRequest("CAPTCHA verification failed. Please try again.");
                    }
                    JsonNode json;
                    try {
                        json = objectMapper.readTree(response.body());
                    } catch (Exception e) {
                        LOGGER.error("Turnstile siteverify returned an unparseable body; rejecting submission", e);
                        throw badRequest("CAPTCHA verification failed. Please try again.");
                    }
                    if (!json.path("success").asBoolean(false)) {
                        LOGGER.warn("Turnstile verification failed: {}", json.path("error-codes").toString());
                        throw badRequest("CAPTCHA verification failed. Please try again.");
                    }
                    verifyHostname(json);
                    return (Void) null;
                })
                .onFailure().transform(t -> {
                    if (t instanceof WebApplicationException wae) {
                        return wae;
                    }
                    if (t instanceof CompletionException && t.getCause() instanceof WebApplicationException wae) {
                        return wae;
                    }
                    LOGGER.error("Turnstile siteverify call failed; rejecting submission (fail closed)", t);
                    return badRequest("CAPTCHA verification failed. Please try again.");
                });
    }

    /**
     * Rejects a token whose siteverify response reports a hostname outside the
     * configured allow-list. This stops a copied (public) sitekey being used to
     * generate tokens on an unrelated site. When no allow-list is configured the
     * check is skipped (a warning is logged at startup).
     *
     * @param json the parsed siteverify response
     * @throws WebApplicationException 400 when the hostname is not allowed
     */
    private void verifyHostname(final JsonNode json) {
        if (allowedHostnames.isEmpty()) {
            return;
        }
        String hostname = json.path("hostname").asText("").toLowerCase();
        if (!allowedHostnames.contains(hostname)) {
            LOGGER.warn("Turnstile hostname '{}' is not in the allowed list {}; rejecting submission",
                    hostname, allowedHostnames);
            throw badRequest("CAPTCHA verification failed. Please try again.");
        }
    }

    private static String urlEncode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static WebApplicationException badRequest(final String message) {
        return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorMessage(message))
                .build());
    }
}
