package org.magic.supportService.app;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.conversions.Bson;
import org.magic.pyramidDraft.app.GameCoordination.MongoService;
import org.magic.supportService.api.SupportRequest;
import org.magic.supportService.api.SupportStatus;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * MongoDB data access handler for support requests. Provides CRUD operations
 * for {@link SupportRequest} documents, including status updates and hard/soft deletion.
 */
@ApplicationScoped
public class SupportDbHandler {
    private static final Logger LOGGER = LogManager.getLogger(SupportDbHandler.class);
    private static final String COLLECTION_NAME = "SupportRequests";

    @Inject
    MongoService mongoService;

    private MongoCollection<SupportRequest> getCollection() {
        MongoDatabase database = mongoService.getDatabase();
        return database.getCollection(COLLECTION_NAME, SupportRequest.class);
    }

    /**
     * Persists a new support request to the database.
     * The request's ID must be pre-set before calling this method.
     *
     * @param request the request to insert
     */
    public void addRequest(final SupportRequest request) {
        getCollection().insertOne(request);
    }

    /**
     * Finds a support request by its ID.
     *
     * @param id the request ID to search for
     * @return the {@link SupportRequest} document
     * @throws IllegalStateException if no request is found with the given ID
     */
    public SupportRequest findById(final String id) {
        Bson filter = Filters.eq("_id", id);
        SupportRequest request = getCollection().find(filter).first();

        if (request == null) {
            LOGGER.error("Unable to find SupportRequest with ID: {}", id);
            throw new IllegalStateException("Unable to find SupportRequest with ID: " + id);
        }
        return request;
    }

    /**
     * Returns all support requests, sorted by creation date descending (newest first).
     *
     * @return the list of all support requests
     */
    public List<SupportRequest> findAll() {
        return getCollection()
                .find()
                .sort(Sorts.descending("createdOnDate"))
                .into(new ArrayList<>());
    }

    /**
     * Updates a support request's mutable fields (title, description, contactEmail, priority, type).
     * Does not modify id, accountID, status, or timestamps.
     *
     * @param request the request with updated fields
     * @return the updated request
     * @throws IllegalStateException if the update fails
     */
    public SupportRequest updateRequest(final SupportRequest request) {
        Bson filter = Filters.eq("_id", request.getId());
        Bson update = Updates.combine(
                Updates.set("title", request.getTitle()),
                Updates.set("description", request.getDescription()),
                Updates.set("contactEmail", request.getContactEmail()),
                Updates.set("priority", request.getPriority()),
                Updates.set("type", request.getType())
        );

        UpdateResult result = getCollection().updateOne(filter, update);

        if (result.getModifiedCount() > 0) {
            LOGGER.info("Successfully updated SupportRequest: {}", request.getId());
            return request;
        } else {
            LOGGER.error("Unable to update SupportRequest: {}", request.getId());
            throw new IllegalStateException("Unable to update SupportRequest: " + request.getId());
        }
    }

    /**
     * Updates the status and lastStatusChangeDate for a support request.
     *
     * @param id        the request ID
     * @param status    the new status
     * @param changeDate the timestamp of the status change
     * @return the updated request
     * @throws IllegalStateException if the update fails
     */
    public SupportRequest updateStatus(final String id, final SupportStatus status, final Instant changeDate) {
        Bson filter = Filters.eq("_id", id);
        Bson update = Updates.combine(
                Updates.set("status", status),
                Updates.set("lastStatusChangeDate", changeDate)
        );

        UpdateResult result = getCollection().updateOne(filter, update);

        if (result.getModifiedCount() > 0) {
            LOGGER.info("Successfully updated status for SupportRequest: {} → {}", id, status);
            return findById(id);
        } else {
            LOGGER.error("Unable to update status for SupportRequest: {}", id);
            throw new IllegalStateException("Unable to update status for SupportRequest: " + id);
        }
    }

    /**
     * Permanently removes a support request from the database.
     * Use for admin/developer cleanup only — prefer status-based soft delete for normal operations.
     *
     * @param id the request ID to delete
     * @return {@code true} if the request was deleted, {@code false} if not found
     */
    public boolean hardDeleteRequest(final String id) {
        Bson filter = Filters.eq("_id", id);
        DeleteResult result = getCollection().deleteOne(filter);

        if (result.getDeletedCount() > 0) {
            LOGGER.info("Hard deleted SupportRequest: {}", id);
            return true;
        }
        LOGGER.warn("SupportRequest not found for hard delete: {}", id);
        return false;
    }
}
