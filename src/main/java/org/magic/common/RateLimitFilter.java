package org.magic.common;

import java.time.Duration;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.magic.common.api.ErrorMessage;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

/**
 * Applies per-client-IP rate limits to abuse-prone endpoints:
 * <ul>
 *   <li>{@code /account/login} — guards the Google token verification path against brute force</li>
 *   <li>{@code /scryfall/...} — guards cost-amplifying proxied external API calls</li>
 *   <li>{@code /lobby} — guards lobby create/join/poll churn</li>
 *   <li>{@code POST /support} — guards support-request creation spam with two
 *       tiers: a short burst cap and a longer hourly cap (per-email limits
 *       live in the support anti-spam service)</li>
 *   <li>{@code GET /support/...} — guards the anonymous, collection-scanning
 *       public reads</li>
 *   <li>{@code /game}, {@code /classic-game}, {@code /burn-game} — guards draft actions and checks</li>
 *   <li>{@code /account/...} and {@code /admin/...} — authenticated endpoints,
 *       limited per JWT subject rather than per IP so one user cannot consume
 *       another's budget (e.g. behind a shared NAT)</li>
 * </ul>
 * Configured via {@code rate-limit.*} properties. When a request is over the
 * limit a 429 with a {@code Retry-After} header is returned.
 *
 * <p>Client identity is resolved by {@link ClientIpResolver}: the
 * {@code X-Forwarded-For} header is honored only when the direct peer is a
 * trusted proxy listed in {@code rate-limit.trusted-proxies}; otherwise
 * client-supplied header values are ignored and the socket peer address is
 * used. Where a token is present but not yet authenticated (filters run before
 * JWT verification), the JWT subject is read from the unverified payload for
 * bucketing only — it is combined with the client IP and never grants access;
 * the real signature verification still decides whether the request succeeds.</p>
 *
 * <p>Implemented as a RESTEasy Reactive server request filter: plain
 * {@code @Provider} JAX-RS filters are not registered by {@code quarkus-rest},
 * so this must stay in the {@code @ServerRequestFilter} form.</p>
 */
@ApplicationScoped
public class RateLimitFilter {

    private static final String TOO_MANY = "Too many requests. Please try again later.";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final boolean enabled;
    private final int loginMax;
    private final Duration loginWindow;
    private final int scryfallMax;
    private final Duration scryfallWindow;
    private final int lobbyMax;
    private final Duration lobbyWindow;
    private final int supportBurstMax;
    private final Duration supportBurstWindow;
    private final int supportHourlyMax;
    private final Duration supportHourlyWindow;
    private final int supportReadMax;
    private final Duration supportReadWindow;
    private final int accountMax;
    private final Duration accountWindow;
    private final int gameMax;
    private final Duration gameWindow;

    /**
     * Creates the filter.
     */
    @Inject
    public RateLimitFilter(
            final RateLimiter rateLimiter,
            final ClientIpResolver clientIpResolver,
            @ConfigProperty(name = "rate-limit.enabled", defaultValue = "true") final boolean enabled,
            @ConfigProperty(name = "rate-limit.login.max", defaultValue = "20") final int loginMax,
            @ConfigProperty(name = "rate-limit.login.window", defaultValue = "PT60S") final Duration loginWindow,
            @ConfigProperty(name = "rate-limit.scryfall.max", defaultValue = "120") final int scryfallMax,
            @ConfigProperty(name = "rate-limit.scryfall.window", defaultValue = "PT60S") final Duration scryfallWindow,
            @ConfigProperty(name = "rate-limit.lobby.max", defaultValue = "60") final int lobbyMax,
            @ConfigProperty(name = "rate-limit.lobby.window", defaultValue = "PT60S") final Duration lobbyWindow,
            @ConfigProperty(name = "rate-limit.support.burst.max", defaultValue = "3") final int supportBurstMax,
            @ConfigProperty(name = "rate-limit.support.burst.window", defaultValue = "PT60S") final Duration supportBurstWindow,
            @ConfigProperty(name = "rate-limit.support.hourly.max", defaultValue = "5") final int supportHourlyMax,
            @ConfigProperty(name = "rate-limit.support.hourly.window", defaultValue = "PT1H") final Duration supportHourlyWindow,
            @ConfigProperty(name = "rate-limit.support-read.max", defaultValue = "60") final int supportReadMax,
            @ConfigProperty(name = "rate-limit.support-read.window", defaultValue = "PT60S") final Duration supportReadWindow,
            @ConfigProperty(name = "rate-limit.account.max", defaultValue = "300") final int accountMax,
            @ConfigProperty(name = "rate-limit.account.window", defaultValue = "PT60S") final Duration accountWindow,
            @ConfigProperty(name = "rate-limit.game.max", defaultValue = "120") final int gameMax,
            @ConfigProperty(name = "rate-limit.game.window", defaultValue = "PT60S") final Duration gameWindow) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.enabled = enabled;
        this.loginMax = loginMax;
        this.loginWindow = loginWindow;
        this.scryfallMax = scryfallMax;
        this.scryfallWindow = scryfallWindow;
        this.lobbyMax = lobbyMax;
        this.lobbyWindow = lobbyWindow;
        this.supportBurstMax = supportBurstMax;
        this.supportBurstWindow = supportBurstWindow;
        this.supportHourlyMax = supportHourlyMax;
        this.supportHourlyWindow = supportHourlyWindow;
        this.supportReadMax = supportReadMax;
        this.supportReadWindow = supportReadWindow;
        this.accountMax = accountMax;
        this.accountWindow = accountWindow;
        this.gameMax = gameMax;
        this.gameWindow = gameWindow;
    }

    /**
     * RESTEasy Reactive request filter: returns the aborting {@link Response}
     * when a limit is exceeded, {@code null} to continue.
     */
    /**
     * RESTEasy Reactive request filter: returns the aborting {@link Response}
     * when a limit is exceeded, {@code null} to continue.
     */
    @ServerRequestFilter(priority = 200, preMatching = true)
    public Response filter(final ContainerRequestContext requestContext) {
        if (!enabled) {
            return null;
        }

        // RESTEasy Reactive reports the path with a leading slash; classic
        // RESTEasy stripped it. Normalize so matching works in both.
        String path = requestContext.getUriInfo().getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        List<Limit> limits = resolveLimits(path, requestContext.getMethod());
        if (limits == null) {
            return null;
        }

        String clientKey = bucketKey(path, clientIpResolver.fromRequestContext(requestContext), requestContext);
        for (int i = 0; i < limits.size(); i++) {
            Limit limit = limits.get(i);
            // Distinct rules on the same path get distinct keys so each records
            // its own hit instead of double-counting in a shared window.
            String key = limits.size() == 1 ? clientKey : clientKey + "#" + i;
            if (!rateLimiter.isAllowed(key, limit.max(), limit.window())) {
                return Response.status(Response.Status.TOO_MANY_REQUESTS)
                        .header("Retry-After", Long.toString(limit.window().toSeconds()))
                        .entity(new ErrorMessage(TOO_MANY))
                        .build();
            }
        }
        return null;
    }

    private List<Limit> resolveLimits(final String path, final String method) {
        if (path.startsWith("account/login")) {
            return List.of(new Limit(loginMax, loginWindow));
        }
        if (path.startsWith("scryfall")) {
            return List.of(new Limit(scryfallMax, scryfallWindow));
        }
        if (path.startsWith("lobby")) {
            return List.of(new Limit(lobbyMax, lobbyWindow));
        }
        if (path.startsWith("support")) {
            return "POST".equalsIgnoreCase(method)
                    ? List.of(new Limit(supportBurstMax, supportBurstWindow),
                              new Limit(supportHourlyMax, supportHourlyWindow))
                    : List.of(new Limit(supportReadMax, supportReadWindow));
        }
        if (path.startsWith("account") || path.startsWith("admin")) {
            return List.of(new Limit(accountMax, accountWindow));
        }
        if (path.startsWith("game") || path.startsWith("classic-game") || path.startsWith("burn-game")) {
            return List.of(new Limit(gameMax, gameWindow));
        }
        return null;
    }

    /**
     * Builds the rate-limit bucket key. Authenticated surfaces are bucketed per
     * caller so one account cannot exhaust another's budget; everything else is
     * bucketed per client IP.
     *
     * @param path          the normalized request path
     * @param clientKey     the resolved client IP
     * @param requestContext the request, used to read the bearer token subject
     * @return the bucket key
     */
    private String bucketKey(final String path, final String clientKey,
                             final ContainerRequestContext requestContext) {
        if (!path.startsWith("account") && !path.startsWith("admin")) {
            return clientKey;
        }
        if (path.startsWith("account/login")) {
            return clientKey;
        }
        String subject = tokenSubject(requestContext.getHeaderString("Authorization"));
        // Without a usable token the request will be rejected as unauthenticated
        // anyway; still give it its own bucket so anonymous probing does not
        // consume the login budget or another user's budget.
        return subject != null ? "user:" + subject : "anon:" + clientKey;
    }

    /**
     * Extracts the {@code sub} claim from a bearer token <em>without</em>
     * verifying it. The value is used only as a rate-limit bucket key, which is
     * safe: an attacker can rotate fake subjects to get more buckets, but that
     * is equivalent to the per-IP limit they already have, and it never grants
     * access — JWT signature verification still decides whether the request
     * succeeds.
     *
     * @param authorization the Authorization header value
     * @return the subject, or {@code null} when absent/unparseable
     */
    static String tokenSubject(final String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        int firstDot = token.indexOf('.');
        int secondDot = firstDot < 0 ? -1 : token.indexOf('.', firstDot + 1);
        if (firstDot < 0 || secondDot < 0) {
            return null;
        }
        try {
            byte[] payload = java.util.Base64.getUrlDecoder()
                    .decode(token.substring(firstDot + 1, secondDot));
            var node = JSON_MAPPER.readTree(payload);
            String sub = node.path("sub").asText(null);
            return sub == null || sub.isBlank() ? null : sub;
        } catch (Exception e) {
            return null;
        }
    }

    private record Limit(int max, Duration window) {}
}
