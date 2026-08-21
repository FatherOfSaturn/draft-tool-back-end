package org.magic.common;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.magic.common.api.ErrorMessage;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Applies per-client-IP rate limits to abuse-prone endpoints:
 * <ul>
 *   <li>{@code /account/login} — guards the Google token verification path against brute force</li>
 *   <li>{@code /scryfall/...} — guards cost-amplifying proxied external API calls</li>
 *   <li>{@code /lobby} — guards lobby create/join/poll churn</li>
 *   <li>{@code POST /support} — guards support-request creation spam</li>
 *   <li>{@code /game}, {@code /classic-game} — guards draft actions, checks, and merges</li>
 * </ul>
 * Configured via {@code rate-limit.*} properties. When a request is over the
 * limit a 429 with a {@code Retry-After} header is returned.
 *
 * <p>Client identity comes from the {@code X-Forwarded-For} header only when
 * the direct peer is a trusted proxy listed in {@code rate-limit.trusted-proxies};
 * otherwise client-supplied header values are ignored and the socket peer
 * address is used. When {@code rate-limit.trusted-proxies} is empty the header
 * is never trusted.</p>
 */
@Provider
public class RateLimitFilter implements ContainerRequestFilter {

    private static final String TOO_MANY = "Too many requests. Please try again later.";
    private static final String FORWARDED_HEADER = "X-Forwarded-For";
    private static final String REMOTE_ADDRESS_PROPERTY = "quarkus.remote-address";

    private final RateLimiter rateLimiter;
    private final boolean enabled;
    private final boolean trustForwardedHeader;
    private final Set<String> trustedProxies;
    private final int loginMax;
    private final Duration loginWindow;
    private final int scryfallMax;
    private final Duration scryfallWindow;
    private final int lobbyMax;
    private final Duration lobbyWindow;
    private final int supportMax;
    private final Duration supportWindow;
    private final int gameMax;
    private final Duration gameWindow;

    @Inject
    public RateLimitFilter(
            final RateLimiter rateLimiter,
            @ConfigProperty(name = "rate-limit.enabled", defaultValue = "true") final boolean enabled,
            @ConfigProperty(name = "rate-limit.trust-forwarded-header", defaultValue = "false") final boolean trustForwardedHeader,
            @ConfigProperty(name = "rate-limit.trusted-proxies", defaultValue = "") final Optional<String> trustedProxies,
            @ConfigProperty(name = "rate-limit.login.max", defaultValue = "20") final int loginMax,
            @ConfigProperty(name = "rate-limit.login.window", defaultValue = "PT60S") final Duration loginWindow,
            @ConfigProperty(name = "rate-limit.scryfall.max", defaultValue = "120") final int scryfallMax,
            @ConfigProperty(name = "rate-limit.scryfall.window", defaultValue = "PT60S") final Duration scryfallWindow,
            @ConfigProperty(name = "rate-limit.lobby.max", defaultValue = "60") final int lobbyMax,
            @ConfigProperty(name = "rate-limit.lobby.window", defaultValue = "PT60S") final Duration lobbyWindow,
            @ConfigProperty(name = "rate-limit.support.max", defaultValue = "20") final int supportMax,
            @ConfigProperty(name = "rate-limit.support.window", defaultValue = "PT60S") final Duration supportWindow,
            @ConfigProperty(name = "rate-limit.game.max", defaultValue = "120") final int gameMax,
            @ConfigProperty(name = "rate-limit.game.window", defaultValue = "PT60S") final Duration gameWindow) {
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
        this.trustForwardedHeader = trustForwardedHeader;
        this.trustedProxies = trustedProxies
                .filter(value -> !value.isBlank())
                .map(value -> (Set<String>) Set.copyOf(Arrays.asList(value.split(","))))
                .orElseGet(Set::of);
        this.loginMax = loginMax;
        this.loginWindow = loginWindow;
        this.scryfallMax = scryfallMax;
        this.scryfallWindow = scryfallWindow;
        this.lobbyMax = lobbyMax;
        this.lobbyWindow = lobbyWindow;
        this.supportMax = supportMax;
        this.supportWindow = supportWindow;
        this.gameMax = gameMax;
        this.gameWindow = gameWindow;
    }

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        if (!enabled) {
            return;
        }

        Limit limit = resolveLimit(requestContext.getUriInfo().getPath(), requestContext.getMethod());
        if (limit == null) {
            return;
        }

        String clientKey = clientKey(requestContext);
        if (!rateLimiter.isAllowed(clientKey, limit.max, limit.window)) {
            requestContext.abortWith(Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .header("Retry-After", Long.toString(limit.window.toSeconds()))
                    .entity(new ErrorMessage(TOO_MANY))
                    .build());
        }
    }

    private Limit resolveLimit(final String path, final String method) {
        if (path.startsWith("account/login")) {
            return new Limit(loginMax, loginWindow);
        }
        if (path.startsWith("scryfall")) {
            return new Limit(scryfallMax, scryfallWindow);
        }
        if (path.startsWith("lobby")) {
            return new Limit(lobbyMax, lobbyWindow);
        }
        if (path.startsWith("support") && "POST".equalsIgnoreCase(method)) {
            return new Limit(supportMax, supportWindow);
        }
        if (path.startsWith("game") || path.startsWith("classic-game")) {
            return new Limit(gameMax, gameWindow);
        }
        return null;
    }

    private String clientKey(final ContainerRequestContext requestContext) {
        String remote = remoteAddress(requestContext);
        if (trustForwardedHeader && remote != null && isTrustedProxy(remote)) {
            List<String> forwarded = requestContext.getHeaders().get(FORWARDED_HEADER);
            if (forwarded != null && !forwarded.isEmpty() && forwarded.get(0) != null
                    && !forwarded.get(0).isBlank()) {
                return forwarded.get(0).trim();
            }
        }
        return remote != null ? remote : requestContext.getUriInfo().getRequestUri().getHost();
    }

    private boolean isTrustedProxy(final String remote) {
        return trustedProxies.isEmpty() ? false : trustedProxies.contains(remote.trim());
    }

    private String remoteAddress(final ContainerRequestContext requestContext) {
        Object property = requestContext.getProperty(REMOTE_ADDRESS_PROPERTY);
        return property != null ? (String) property : null;
    }

    private record Limit(int max, Duration window) {}
}
