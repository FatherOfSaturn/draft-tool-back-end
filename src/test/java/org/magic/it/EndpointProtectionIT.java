package org.magic.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.magic.common.RateLimiter;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * Verifies that every top-level resource path is covered by a rate-limit rule:
 * each endpoint family is hit repeatedly with its limit set to 1, so the second
 * request must be rejected with 429 rather than reaching the resource.
 *
 * <p>This guards against the class of bug where a filter exists but is not
 * wired in, or a path prefix is missing from the rule table.</p>
 */
@QuarkusTest
@Tag("mongo")
@TestProfile(EndpointProtectionIT.Profile.class)
class EndpointProtectionIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("rate-limit.enabled", "true"),
                    Map.entry("rate-limit.login.max", "1"),
                    Map.entry("rate-limit.login.window", "PT60S"),
                    Map.entry("rate-limit.scryfall.max", "1"),
                    Map.entry("rate-limit.scryfall.window", "PT60S"),
                    Map.entry("rate-limit.lobby.max", "1"),
                    Map.entry("rate-limit.lobby.window", "PT60S"),
                    Map.entry("rate-limit.support.burst.max", "1"),
                    Map.entry("rate-limit.support.burst.window", "PT60S"),
                    Map.entry("rate-limit.support.hourly.max", "1"),
                    Map.entry("rate-limit.support.hourly.window", "PT1H"),
                    Map.entry("rate-limit.support-read.max", "1"),
                    Map.entry("rate-limit.support-read.window", "PT60S"),
                    Map.entry("rate-limit.account.max", "1"),
                    Map.entry("rate-limit.account.window", "PT60S"),
                    Map.entry("rate-limit.game.max", "1"),
                    Map.entry("rate-limit.game.window", "PT60S"),
                    Map.entry("rate-limit.support-email.enabled", "false"),
                    Map.entry("support.anti-spam.mode", "off"),
                    Map.entry("turnstile.enabled", "false"),
                    Map.entry("scryfall.cache.enabled", "false"));
        }
    }

    /**
     * Every {@code @Path} prefix in the application, with a concrete sample
     * request. Limits are 1, so the first may succeed and the second must 429.
     * Paths are distinct per probe because support reads share one bucket.
     */
    private static final List<Runnable> PROBES = List.of(
            () -> assertSecondAttemptLimited("GET", "/account", ""),
            () -> assertSecondAttemptLimited("POST", "/support", """
                    {"title":"t","description":"d","contactEmail":"e@example.com",
                     "priority":"low","type":"misc_support"}"""),
            () -> assertSecondAttemptLimited("GET", "/scryfall/cards/nonexistent-card-id", ""),
            () -> assertSecondAttemptLimited("GET", "/lobby/ABC12345", ""),
            () -> assertSecondAttemptLimited("GET", "/game/does-not-exist/draftCheck", ""),
            () -> assertSecondAttemptLimited("GET", "/classic-game/does-not-exist/draftCheck", ""),
            () -> assertSecondAttemptLimited("GET", "/burn-game/does-not-exist/draftCheck", ""),
            () -> assertSecondAttemptLimited("GET", "/admin/check", ""));

    @Inject
    RateLimiter rateLimiter;

    /** Each scenario needs a clean budget; buckets are process-wide. */
    @BeforeEach
    void resetLimiter() {
        rateLimiter.reset();
    }

    /** Login is bucketed per IP; probe it in isolation for a clean budget. */
    @Test
    void loginIsRateLimited() {
        assertSecondAttemptLimited("POST", "/account/login", "");
    }

    /** Anonymous support reads share a single bucket, so probe them separately. */
    @Test
    void anonymousSupportReadsAreRateLimited() {
        assertSecondAttemptLimited("GET", "/support/public", "");
    }

    @Test
    void everyEndpointFamilyIsRateLimited() {
        for (int i = 0; i < PROBES.size(); i++) {
            rateLimiter.reset();
            PROBES.get(i).run();
        }
    }

    /**
     * Sends the same request until it is throttled and asserts a 429 appears.
     * Limits in this profile are 1, so this must happen on the second attempt.
     *
     * @param method  the HTTP method
     * @param path    the request path
     * @param body    a JSON body (ignored for GET)
     */
    private static void assertSecondAttemptLimited(final String method, final String path, final String body) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            var request = given().contentType("application/json");
            if (!body.isEmpty()) {
                request = request.body(body);
            }
            int code = "GET".equals(method)
                    ? request.when().get(path).then().extract().statusCode()
                    : request.when().post(path).then().extract().statusCode();
            if (code == 429) {
                assertTrue(attempt >= 2,
                        method + " " + path + " was throttled on the first request; "
                                + "limit of 1 should allow one through");
                return;
            }
        }
        throw new AssertionError("expected " + method + " " + path + " to be rate-limited with 429, never saw it");
    }
}
