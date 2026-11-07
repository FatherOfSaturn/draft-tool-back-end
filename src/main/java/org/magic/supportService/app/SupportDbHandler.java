package org.magic.supportService.app;

import java.time.Instant;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.conversions.Bson;
import org.magic.supportService.api.SupportRequest;
import org.magic.supportService.api.SupportStatus;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import io.quarkus.mongodb.FindOptions;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive MongoDB data access handler for support requests. Provides CRUD
 * operations for {@link SupportRequest} documents, including status updates and
 * hard/soft deletion.
 */
@ApplicationScoped
public class SupportDbHandler implements ReactivePanacheMongoRepository<SupportRequest> {
    private static final Logger LOGGER = LogManager.getLogger(SupportDbHandler.class);

    /**
     * Persists a new support request to the database.
     * The request's ID must be pre-set before calling this method.
     *
     * @param request the request to insert
     */
    public Uni<Void> addRequest(final SupportRequest request) {
        return mongoCollection().insertOne(request).replaceWithVoid();
    }

    /**
     * Finds a support request by its ID.
     *
     * @param id the request ID to search for
     * @return a {@link Uni} emitting the {@link SupportRequest} document
     * @throws IllegalStateException if no request is found with the given ID
     */
    public Uni<SupportRequest> findById(final String id) {
        Bson filter = Filters.eq("_id", id);
        return mongoCollection().find(filter).collect().first()
                .onItem().ifNull().failWith(() -> {
                    LOGGER.error("Unable to find SupportRequest with ID: {}", id);
                    return new IllegalStateException("Unable to find SupportRequest with ID: " + id);
                });
    }

    /**
     * Returns all support requests, sorted by creation date descending (newest first).
     *
     * @return a {@link Uni} emitting the list of all support requests
     */
    public Uni<List<SupportRequest>> findAllRequests() {
        FindOptions options = new FindOptions().sort(Sorts.descending("createdOnDate"));
        return mongoCollection().find(options).collect().asList();
    }

    /**
     * Updates a support request's mutable fields (title, description, contactEmail, priority, type).
     * Does not modify id, accountID, status, or timestamps.
     *
     * @param request the request with updated fields
     * @return a {@link Uni} emitting the updated request
     * @throws IllegalStateException if the update fails
     */
    public Uni<SupportRequest> updateRequest(final SupportRequest request) {
        Bson filter = Filters.eq("_id", request.getId());
        Bson update = Updates.combine(
                Updates.set("title", request.getTitle()),
                Updates.set("description", request.getDescription()),
                Updates.set("contactEmail", request.getContactEmail()),
                Updates.set("priority", request.getPriority()),
                Updates.set("type", request.getType())
        );

        return mongoCollection().updateOne(filter, update)
                .map(result -> {
                    if (result.getModifiedCount() > 0) {
                        LOGGER.info("Successfully updated SupportRequest: {}", request.getId());
                        return request;
                    }
                    LOGGER.error("Unable to update SupportRequest: {}", request.getId());
                    throw new IllegalStateException("Unable to update SupportRequest: " + request.getId());
                });
    }

    /**
     * Updates the status and lastStatusChangeDate for a support request.
     *
     * @param id         the request ID
     * @param status     the new status
     * @param changeDate the timestamp of the status change
     * @return a {@link Uni} emitting the updated request
     * @throws IllegalStateException if the update fails
     */
    public Uni<SupportRequest> updateStatus(final String id, final SupportStatus status, final Instant changeDate) {
        Bson filter = Filters.eq("_id", id);
        Bson update = Updates.combine(
                Updates.set("status", status),
                Updates.set("lastStatusChangeDate", changeDate)
        );

        return mongoCollection().updateOne(filter, update)
                .onItem().transformToUni(result -> {
                    if (result.getModifiedCount() > 0) {
                        LOGGER.info("Successfully updated status for SupportRequest: {} → {}", id, status);
                        return findById(id);
                    }
                    LOGGER.error("Unable to update status for SupportRequest: {}", id);
                    return Uni.createFrom().failure(
                            new IllegalStateException("Unable to update status for SupportRequest: " + id));
                });
    }

    /**
     * Permanently removes a support request from the database.
     *
     * @param id the request ID to delete
     * @return a {@link Uni} emitting {@code true} if the request was deleted
     */
    public Uni<Boolean> hardDeleteRequest(final String id) {
        Bson filter = Filters.eq("_id", id);
        return mongoCollection().deleteOne(filter)
                .map(result -> {
                    if (result.getDeletedCount() > 0) {
                        LOGGER.info("Hard deleted SupportRequest: {}", id);
                        return true;
                    }
                    LOGGER.warn("SupportRequest not found for hard delete: {}", id);
                    return false;
                });
    }
}
