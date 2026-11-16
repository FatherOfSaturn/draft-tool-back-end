package org.magic.supportService.app;

import java.time.Instant;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.types.ObjectId;
import org.magic.supportService.api.CreateSupportRequest;
import org.magic.supportService.api.SupportRequest;
import org.magic.supportService.api.SupportStatus;
import org.magic.supportService.api.UpdateSupportRequest;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reactive service layer for support requests. Handles creation, retrieval,
 * updates, status changes, and deletion of {@link SupportRequest} documents.
 * Delegates persistence to {@link SupportDbHandler}.
 *
 * <p>Creation runs the anti-spam gate first ({@link AntiSpamService}): the
 * honeypot short-circuits with a non-persisted fake success, mode-gated
 * form-token/Turnstile checks can reject, the per-email limit can reject with
 * 429, and a spam-looking description is quarantined rather than dropped.</p>
 */
@ApplicationScoped
public class SupportWorker {
    private static final Logger LOGGER = LogManager.getLogger(SupportWorker.class);

    private final SupportDbHandler dbHandler;
    private final AntiSpamService antiSpamService;

    @Inject
    public SupportWorker(final SupportDbHandler dbHandler, final AntiSpamService antiSpamService) {
        this.dbHandler = dbHandler;
        this.antiSpamService = antiSpamService;
    }

    /**
     * Handles a new support-request submission.
     *
     * <p>Order of checks: honeypot → mode-gated form-token/Turnstile →
     * per-email rate limit → spam heuristic → persist. A honeypot hit returns
     * an id-shaped success response for an entity that is never saved, so the
     * caller cannot distinguish it from a real submission.</p>
     *
     * @param request the creation payload
     * @param clientIp the resolved client IP (used for Turnstile {@code remoteip})
     * @return a {@link Uni} emitting the persisted {@link SupportRequest} (or
     *         the non-persisted honeypot placeholder) with a generated ID
     */
    public Uni<SupportRequest> createRequest(final CreateSupportRequest request, final String clientIp) {
        if (antiSpamService.isHoneypot(request)) {
            Instant now = Instant.now();
            SupportRequest fake = new SupportRequest(
                    null,
                    request.title(),
                    request.description(),
                    request.contactEmail(),
                    request.priority(),
                    null,
                    SupportStatus.NEW,
                    request.type(),
                    now,
                    now);
            fake.setId(new ObjectId().toHexString());
            return Uni.createFrom().item(fake);
        }

        return antiSpamService.prePersistChecks(request, clientIp)
                .invoke(() -> antiSpamService.checkEmailRate(request.contactEmail()))
                .chain(ignored -> persist(request));
    }

    private Uni<SupportRequest> persist(final CreateSupportRequest request) {
        Instant now = Instant.now();
        SupportStatus status = SupportSpamHeuristic.isLikelySpam(request.description())
                ? SupportStatus.QUARANTINED
                : SupportStatus.NEW;
        SupportRequest supportRequest = new SupportRequest(
                null,
                request.title(),
                request.description(),
                request.contactEmail(),
                request.priority(),
                null,
                status,
                request.type(),
                now,
                now
        );
        supportRequest.setId(new ObjectId().toHexString());

        return dbHandler.addRequest(supportRequest)
                .invoke(ignored -> LOGGER.info("Created SupportRequest: {} - {} ({})",
                        supportRequest.getId(), request.title(), status))
                .replaceWith(supportRequest);
    }

    /**
     * Retrieves a support request by its ID.
     *
     * @param id the request ID
     * @return a {@link Uni} emitting the {@link SupportRequest}
     * @throws IllegalStateException if no request is found
     */
    public Uni<SupportRequest> getRequest(final String id) {
        return dbHandler.findRequestById(id);
    }

    /**
     * Returns all support requests, sorted by creation date descending.
     *
     * @return a {@link Uni} emitting the list of all support requests
     */
    public Uni<List<SupportRequest>> getAllRequests() {
        return dbHandler.findAllRequests();
    }

    /**
     * Updates the mutable fields of an existing support request.
     * Only non-null fields in the update payload are applied.
     *
     * @param id the request ID
     * @param request the fields to update
     * @return a {@link Uni} emitting the updated {@link SupportRequest}
     * @throws IllegalStateException if the request is not found
     */
    public Uni<SupportRequest> updateRequest(final String id, final UpdateSupportRequest request) {
        return dbHandler.findRequestById(id)
                .chain(existing -> {
                    if (request.title() != null) {
                        existing.setTitle(request.title());
                    }
                    if (request.description() != null) {
                        existing.setDescription(request.description());
                    }
                    if (request.contactEmail() != null) {
                        existing.setContactEmail(request.contactEmail());
                    }
                    if (request.priority() != null) {
                        existing.setPriority(request.priority());
                    }
                    if (request.type() != null) {
                        existing.setType(request.type());
                    }
                    return dbHandler.updateRequest(existing);
                });
    }

    /**
     * Updates the status of a support request and sets the lastStatusChangeDate
     * to the current time.
     *
     * @param id     the request ID
     * @param status the new status
     * @return a {@link Uni} emitting the updated {@link SupportRequest}
     * @throws IllegalStateException if the request is not found
     */
    public Uni<SupportRequest> updateStatus(final String id, final SupportStatus status) {
        return dbHandler.updateStatus(id, status, Instant.now());
    }

    /**
     * Soft-deletes a support request by setting its status to {@link SupportStatus#DELETED}.
     * The record is preserved in the database.
     *
     * @param id the request ID
     * @return a {@link Uni} emitting the updated {@link SupportRequest}
     * @throws IllegalStateException if the request is not found
     */
    public Uni<SupportRequest> softDeleteRequest(final String id) {
        return updateStatus(id, SupportStatus.DELETED);
    }

    /**
     * Permanently removes a support request from the database.
     * For admin/developer cleanup only.
     *
     * @param id the request ID
     * @return a {@link Uni} emitting {@code true} if deleted, {@code false} if not found
     */
    public Uni<Boolean> deleteRequest(final String id) {
        return dbHandler.hardDeleteRequest(id);
    }
}
