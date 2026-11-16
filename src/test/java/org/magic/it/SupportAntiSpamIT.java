package org.magic.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * End-to-end tests for the enforced anti-spam path: the signed form token
 * (issued by {@code GET /support/form-token}) is rejected when missing,
 * tampered, or submitted faster than the minimum age, and accepted after the
 * window elapses. Turnstile is disabled in this profile, so a valid form token
 * alone lets a submission through.
 */
@QuarkusTest
@Tag("mongo")
@TestProfile(SupportAntiSpamIT.FullModeProfile.class)
class SupportAntiSpamIT {

    /** Enforces the form token; Turnstile stays off, rate limits stay off. */
    public static class FullModeProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "support.anti-spam.mode", "full",
                    "turnstile.enabled", "false",
                    "rate-limit.enabled", "false",
                    "rate-limit.support-email.enabled", "false",
                    "support.form-token.min-age", "PT3S",
                    "support.form-token.max-age", "PT2H");
        }
    }

    private static String submitBody(final String suffix, final String formToken) {
        return """
                {
                  "title": "IT anti-spam %s",
                  "description": "Form token enforcement flow test.",
                  "contactEmail": "it-spam-%s@example.com",
                  "priority": "low",
                  "type": "misc_support",
                  "formToken": %s
                }""".formatted(suffix, suffix,
                formToken == null ? "null" : "\"" + formToken + "\"");
    }

    @Test
    void missingTokenIsRejected() {
        given()
                .contentType("application/json")
                .body(submitBody("missing-" + Long.toHexString(System.nanoTime()), null))
                .when().post("/support")
                .then()
                .statusCode(400);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = given().when().get("/support/form-token")
                .then().statusCode(200).extract().path("token");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + (parts[1].charAt(0) == 'A' ? "B" : "A") + parts[1].substring(1);

        given()
                .contentType("application/json")
                .body(submitBody("tampered-" + Long.toHexString(System.nanoTime()), tampered))
                .when().post("/support")
                .then()
                .statusCode(400);
    }

    @Test
    void garbageTokenIsRejected() {
        given()
                .contentType("application/json")
                .body(submitBody("garbage-" + Long.toHexString(System.nanoTime()), "not.a.token"))
                .when().post("/support")
                .then()
                .statusCode(400);
    }

    @Test
    void instantSubmitIsRejected() {
        String token = given().when().get("/support/form-token")
                .then().statusCode(200).extract().path("token");

        // Submitted well inside the 3-second minimum age window.
        given()
                .contentType("application/json")
                .body(submitBody("instant-" + Long.toHexString(System.nanoTime()), token))
                .when().post("/support")
                .then()
                .statusCode(400);
    }

    @Test
    void validTokenAfterMinAgeIsAccepted() throws Exception {
        String token = given().when().get("/support/form-token")
                .then().statusCode(200).extract().path("token");
        assertFalse(token.isBlank());
        Thread.sleep(3100);

        JsonNode ack = It.tree(given()
                .contentType("application/json")
                .body(submitBody("valid-" + Long.toHexString(System.nanoTime()), token))
                .when().post("/support")
                .then()
                .statusCode(200)
                .extract().asString());

        It.assertFieldOrder(ack, List.of("id"));
        assertFalse(ack.get("id").asText().isBlank());
    }
}
