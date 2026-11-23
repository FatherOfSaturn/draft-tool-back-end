package org.magic.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * Reproduces the deployment path exactly: the mode is supplied only through the
 * environment-variable name Quarkus uses in production
 * ({@code QUARKUS_SUPPORT_ANTI_SPAM_MODE}), not the dotted property.
 *
 * <p>Asserts a {@code support.spam.report_only} counter is incremented, which
 * only happens when the mode resolved to {@code report-only}. A 200 alone is
 * not evidence, because {@code off} also returns 200.</p>
 */
@QuarkusTest
@Tag("mongo")
@TestProfile(EnvVarModeIT.Profile.class)
class EnvVarModeIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Deliberately do NOT set support.anti-spam.mode — only the
            // Quarkus env-var form, as a deployed container would.
            return Map.of(
                    "QUARKUS_SUPPORT_ANTI_SPAM_MODE", "report-only",
                    "turnstile.enabled", "false",
                    "rate-limit.enabled", "false",
                    "rate-limit.support-email.enabled", "false");
        }
    }

    @Inject
    MeterRegistry meterRegistry;

    @Test
    void envVarModeResolvesToReportOnly() {
        double before = meterRegistry.find("support.spam.report_only").counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();

        given()
                .contentType("application/json")
                .body("""
                      {"title":"env-mode","description":"env var mode check",
                       "contactEmail":"env-mode@example.com","priority":"low","type":"misc_support"}""")
                .when().post("/support")
                .then()
                .statusCode(200);

        double after = meterRegistry.find("support.spam.report_only").counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();

        assertTrue(after > before,
                "expected support.spam.report_only to increment, meaning QUARKUS_SUPPORT_ANTI_SPAM_MODE "
                        + "resolved to report-only (before=" + before + ", after=" + after + ")");
    }
}
