package org.magic.supportService.app;

import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.common.RateLimiter;
import org.magic.supportService.api.CreateSupportRequest;
import org.magic.supportService.external.TurnstileVerifier;

import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Anti-spam gate for support-request submissions. Combines:
 *
 * <ul>
 *   <li>a honeypot field ({@code website}) — bots that fill it get a normal
 *       success response but nothing is saved; trips a Micrometer counter,</li>
 *   <li>signed form-token and Cloudflare Turnstile checks, gated by
 *       {@code support.antispam.mode} (see {@link AntiSpamMode}),</li>
 *   <li>a per-normalized-email submission rate limit (the per-IP limits live
 *       in {@link org.magic.common.RateLimitFilter}).</li>
 * </ul>
 */
@ApplicationScoped
public class AntiSpamService {

    private static final Logger LOGGER = LogManager.getLogger(AntiSpamService.class);
    private static final String HONEYPOT_COUNTER = "support.honeypot.triggered";
    private static final String REJECTED_COUNTER = "support.spam.rejected";
    private static final String REPORT_ONLY_COUNTER = "support.spam.report_only";

    private final AntiSpamMode mode;
    private final FormTokenService formTokenService;
    private final TurnstileVerifier turnstileVerifier;
    private final RateLimiter rateLimiter;
    private final boolean emailLimitEnabled;
    private final int emailMax;
    private final Duration emailWindow;    private final io.micrometer.core.instrument.Counter honeypotCounter;
    private final MeterRegistry meterRegistry;

    @Inject
    public AntiSpamService(
            @ConfigProperty(name = "support.antispam.mode", defaultValue = "off") final String mode,
            final FormTokenService formTokenService,
            final TurnstileVerifier turnstileVerifier,
            final RateLimiter rateLimiter,
            final MeterRegistry meterRegistry,
            @ConfigProperty(name = "rate-limit.support-email.enabled", defaultValue = "true") final boolean emailLimitEnabled,
            @ConfigProperty(name = "rate-limit.support-email.max", defaultValue = "3") final int emailMax,
            @ConfigProperty(name = "rate-limit.support-email.window", defaultValue = "PT1H") final Duration emailWindow) {
        this.mode = AntiSpamMode.fromString(mode);
        this.formTokenService = formTokenService;
        this.turnstileVerifier = turnstileVerifier;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
        this.honeypotCounter = meterRegistry.counter(HONEYPOT_COUNTER);
        this.emailLimitEnabled = emailLimitEnabled;
        this.emailMax = emailMax;
        this.emailWindow = emailWindow;
        LOGGER.info("Support anti-spam configuration: mode={} (env SUPPORT_ANTISPAM_MODE), "
                        + "form-token enforced={}, turnstile enabled={}",
                this.mode.getDescription(), this.mode.enforcesFormToken(), turnstileVerifier.isEnabled());
    }

    /**
     * Whether the signed form token is currently enforced. Exposed for
     * deployment-configuration tests and diagnostics.
     *
     * @return {@code true} when a missing/invalid form token rejects a submission
     */
    public boolean isFormTokenEnforced() {
        return mode.enforcesFormToken();
    }

    /**
     * Detects a filled honeypot field. Must be called first: a honeypot hit
     * short-circuits all further checks and persistence.
     *
     * @param request the submitted request
     * @return {@code true} when the hidden {@code website} field is non-blank
     */
    public boolean isHoneypot(final CreateSupportRequest request) {
        boolean triggered = request.website() != null && !request.website().isBlank();
        if (triggered) {
            honeypotCounter.increment();
            LOGGER.info("Support honeypot triggered; submission will be silently discarded");
        }
        return triggered;
    }

    /**
     * Runs the mode-gated form-token and Turnstile checks. In
     * {@link AntiSpamMode#REPORT_ONLY} failures are logged and counted but do
     * not reject; in {@link AntiSpamMode#OFF} nothing runs.
     *
     * @param request the submitted request
     * @param clientIp the resolved client IP, used for Turnstile {@code remoteip}
     * @return a {@link Uni} completing when the checks pass
     */
    public Uni<Void> prePersistChecks(final CreateSupportRequest request, final String clientIp) {
        if (mode == AntiSpamMode.OFF) {
            return Uni.createFrom().voidItem();
        }

        try {
            formTokenService.verify(request.formToken());
        } catch (WebApplicationException e) {
            if (mode.enforcesFormToken()) {
                meterRegistry.counter(REJECTED_COUNTER, "reason", "form-token").increment();
                return Uni.createFrom().failure(e);
            }
            reportOnly("form-token", e);
        }

        if (!mode.runsTurnstile()) {
            return Uni.createFrom().voidItem();
        }
        Uni<Void> turnstile = turnstileVerifier.verify(request.turnstileToken(), clientIp);
        if (mode == AntiSpamMode.REPORT_ONLY) {
            turnstile = turnstile
                    .onFailure().invoke(e -> reportOnly("turnstile", e))
                    .onFailure().recoverWithNull();
        }
        return turnstile;
    }

    /**
     * Applies the per-normalized-email submission limit. Gmail aliases collapse
     * to one identity (see {@link org.magic.common.util.EmailNormalizer}).
     *
     * @param contactEmail the submitter's email address
     * @throws WebApplicationException 429 with a {@code Retry-After} header when
     *         the limit is exceeded
     */
    public void checkEmailRate(final String contactEmail) {
        if (!emailLimitEnabled) {
            return;
        }
        String normalized = org.magic.common.util.EmailNormalizer.normalize(contactEmail);
        if (!rateLimiter.isAllowed("support-email:" + normalized, emailMax, emailWindow)) {
            LOGGER.info("Support email rate limit hit for {}", normalized);
            throw new WebApplicationException(Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .header("Retry-After", Long.toString(emailWindow.toSeconds()))
                    .entity(new org.magic.common.api.ErrorMessage(
                            "Too many requests from this email. Please try again later."))
                    .build());
        }
    }

    private void reportOnly(final String reason, final Throwable failure) {
        meterRegistry.counter(REPORT_ONLY_COUNTER, "reason", reason).increment();
        LOGGER.info("Support anti-spam (report-only) would reject submission: reason={}, error={}",
                reason, failure.getMessage());
    }
}
