package org.magic.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.magic.supportService.app.AntiSpamService;
import org.magic.supportService.external.TurnstileVerifier;

/**
 * Guards against deployment-only configuration being silently ignored.
 *
 * <p>Twice during rollout a property was present in the container environment
 * but had no effect: once because the key contained a hyphen and could not be
 * mapped from an environment variable, and once because the value was a
 * hardcoded literal in {@code application.properties} with no placeholder.
 * Both produced a system that looked configured but ran with checks disabled.
 *
 * <p>This test sets only the production environment-variable names and asserts
 * the application actually resolves them.</p>
 */
@QuarkusTest
@Tag("mongo")
@TestProfile(DeploymentConfigIT.Profile.class)
class DeploymentConfigIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                    // Only the env-var names a deployed container would provide.
                    Map.entry("SUPPORT_ANTISPAM_MODE", "token-only"),
                    Map.entry("TURNSTILE_ENABLED", "true"),
                    Map.entry("TURNSTILE_SECRET_KEY", "it-deployment-test-secret"),
                    Map.entry("SUPPORT_FORM_TOKEN_SECRET", "it-deployment-form-token-secret-0123456789"),
                    Map.entry("rate-limit.enabled", "false"),
                    Map.entry("rate-limit.support-email.enabled", "false"));
        }
    }

    @Inject
    AntiSpamService antiSpamService;

    @Inject
    TurnstileVerifier turnstileVerifier;

    @Test
    void antiSpamModeIsResolvedFromEnvironment() {
        assertTrue(antiSpamService.isFormTokenEnforced(),
                "SUPPORT_ANTISPAM_MODE=token-only must enable form-token enforcement; "
                        + "if this fails the mode fell back to off");
    }

    @Test
    void turnstileEnabledIsResolvedFromEnvironment() {
        assertTrue(turnstileVerifier.isEnabled(),
                "TURNSTILE_ENABLED=true must enable Turnstile verification; "
                        + "if this fails the value is hardcoded or unreadable from the environment");
    }

    @Test
    void turnstileSecretIsResolvedFromEnvironment() {
        assertTrue(turnstileVerifier.isSecretConfigured(),
                "TURNSTILE_SECRET_KEY must reach the verifier");
    }
}
