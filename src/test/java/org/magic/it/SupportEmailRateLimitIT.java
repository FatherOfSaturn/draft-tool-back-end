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
 * End-to-end test of the per-email support limit: dotted, plus-tagged, and
 * differently-cased Gmail variants all normalize to one mailbox, so the third
 * submission is rejected with 429. The IP caps are raised so only the email
 * tier trips.
 */
@QuarkusTest
@Tag("mongo")
@TestProfile(SupportEmailRateLimitIT.Profile.class)
class SupportEmailRateLimitIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    Map.entry("rate-limit.enabled", "true"),
                    Map.entry("rate-limit.support.burst.max", "50"),
                    Map.entry("rate-limit.support.burst.window", "PT60S"),
                    Map.entry("rate-limit.support.hourly.max", "500"),
                    Map.entry("rate-limit.support.hourly.window", "PT1H"),
                    Map.entry("rate-limit.support-email.enabled", "true"),
                    Map.entry("rate-limit.support-email.max", "2"),
                    Map.entry("rate-limit.support-email.window", "PT1H"),
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
    void dottedAndTaggedGmailVariantsCountAsOneIdentity() {
        String suffix = Long.toHexString(System.nanoTime());
        // All three variants normalize to the same Gmail mailbox:
        // it<suffix>joe@gmail.com (dots and +tags stripped, domains unified)
        assertEquals(200, post("email-1-" + suffix, "it." + suffix + ".joe@gmail.com"));
        assertEquals(200, post("email-2-" + suffix, "it." + suffix + ".joe+tag@googlemail.com"));

        // Third variant of the same normalized Gmail mailbox exceeds the cap.
        assertEquals(429, post("email-3-" + suffix, "IT." + suffix + ".JOE+x@gmail.com"));

        // A different provider with dots in the local part is unaffected.
        assertEquals(200, post("email-4-" + suffix, "o.t.h." + suffix + "@example.com"));
    }
}
