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
 * Service layer for support requests. Handles creation, retrieval, updates,
 * status changes, and deletion of {@link SupportRequest} documents.
 * Delegates persistence to {@link SupportDbHandler}.
 */
@ApplicationScoped
public class SupportWorker {
    private static final Logger LOGGER = LogManager.getLogger(SupportWorker.class);

    private final SupportDbHandler dbHandler;

    @Inject
    public SupportWorker(final SupportDbHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    /**
     * Creates a new support request with {@link SupportStatus#NEW} and current timestamps.
     *
     * @param request the creation payload
     * @return the persisted {@link SupportRequest} with generated ID
     */
    public Uni<SupportRequest> createRequest(final CreateSupportRequest request) {
        return Uni.createFrom().item(() -> {
            Instant now = Instant.now();
            SupportRequest supportRequest = new SupportRequest(
                    null,
                    request.title(),
                    request.description(),
                    request.contactEmail(),
                    request.priority(),
                    request.accountID(),
                    SupportStatus.NEW,
                    request.type(),
                    now,
                    now
            );

            supportRequest.setId(new ObjectId().toHexString());
            dbHandler.addRequest(supportRequest);
            LOGGER.info("Created SupportRequest: {} - {}", supportRequest.getId(), request.title());
            return supportRequest;
        });
    }

    /**
     * Retrieves a support request by its ID.
     *
     * @param id the request ID
     * @return the {@link SupportRequest}
     * @throws IllegalStateException if no request is found
     */
    public Uni<SupportRequest> getRequest(final String id) {
        return Uni.createFrom().item(() -> dbHandler.findById(id));
    }

    /**
     * Returns all support requests, sorted by creation date descending.
     *
     * @return the list of all support requests
     */
    public Uni<List<SupportRequest>> getAllRequests() {
        return Uni.createFrom().item(() -> dbHandler.findAll());
    }

    /**
     * Updates the mutable fields of an existing support request.
     * Only non-null fields in the update payload are applied.
     *
     * @param id      the request ID
     * @param request the fields to update
     * @return the updated {@link SupportRequest}
     * @throws IllegalStateException if the request is not found
     */
    public Uni<SupportRequest> updateRequest(final String id, final UpdateSupportRequest request) {
        return Uni.createFrom().item(() -> {
            SupportRequest existing = dbHandler.findById(id);

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
     * @return the updated {@link SupportRequest}
     * @throws IllegalStateException if the request is not found
     */
    public Uni<SupportRequest> updateStatus(final String id, final SupportStatus status) {
        return Uni.createFrom().item(() -> dbHandler.updateStatus(id, status, Instant.now()));
    }

    /**
     * Soft-deletes a support request by setting its status to {@link SupportStatus#DELETED}.
     * The record is preserved in the database.
     *
     * @param id the request ID
     * @return the updated {@link SupportRequest}
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
     * @return {@code true} if deleted, {@code false} if not found
     */
    public Uni<Boolean> deleteRequest(final String id) {
        return Uni.createFrom().item(() -> dbHandler.hardDeleteRequest(id));
    }
}
