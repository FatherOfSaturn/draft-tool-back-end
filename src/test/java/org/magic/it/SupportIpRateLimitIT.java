package org.magic.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * End-to-end test of the per-IP hourly cap on {@code POST /support}: the 6th
 * submission within the window is rejected with 429 + {@code Retry-After}.
 * The burst cap is raised and the per-email limit disabled so only the hourly
 * IP tier trips.
 */
@QuarkusTest
@Tag("mongo")
@TestProfile(SupportIpRateLimitIT.Profile.class)
class SupportIpRateLimitIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("rate-limit.enabled", "true"),
                    Map.entry("rate-limit.support.burst.max", "50"),
                    Map.entry("rate-limit.support.burst.window", "PT60S"),
                    Map.entry("rate-limit.support.hourly.max", "5"),
                    Map.entry("rate-limit.support.hourly.window", "PT1H"),
                    Map.entry("rate-limit.support-email.enabled", "false"),
                    Map.entry("support.anti-spam.mode", "off"),
                    Map.entry("turnstile.enabled", "false"));
        }
    }

    private static int post(final String title, final String email) {
        return given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "%s",
                        "description": "Rate limit flow test.",
                        "contactEmail": "%s",
                        "priority": "low",
                        "type": "misc_support"
                      }""".formatted(title, email))
                .when().post("/support")
                .then()
                .extract().statusCode();
    }

    @Test
    void sixthRequestFromSameIpWithinHourIsRejected() {
        String suffix = Long.toHexString(System.nanoTime());
        for (int i = 0; i < 5; i++) {
            assertEquals(200, post("ip-limit-" + i + "-" + suffix, "it-ip-" + i + "-" + suffix + "@example.com"));
        }

        assertEquals(429, post("ip-limit-6-" + suffix, "it-ip-6-" + suffix + "@example.com"));
        given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "ip-limit-7-%s",
                        "description": "Still over the limit.",
                        "contactEmail": "it-ip-7-%s@example.com",
                        "priority": "low",
                        "type": "misc_support"
                      }""".formatted(suffix, suffix))
                .when().post("/support")
                .then()
                .statusCode(429)
                .header("Retry-After", "3600");
    }
}
