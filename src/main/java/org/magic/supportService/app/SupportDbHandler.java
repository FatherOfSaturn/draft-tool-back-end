package org.magic.supportService.app;

import java.time.Instant;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.conversions.Bson;
import org.magic.common.mongo.ReactiveMongo;
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
 * Reactive repository for support requests. Status/mutable-field updates are
 * targeted {@code $set} operations so concurrent edits keep their timestamps.
 */
@ApplicationScoped
public class SupportDbHandler implements ReactivePanacheMongoRepository<SupportRequest> {
    private static final Logger LOGGER = LogManager.getLogger(SupportDbHandler.class);

    public Uni<Void> addRequest(final SupportRequest request) {
        return persist(request).replaceWithVoid();
    }

    public Uni<SupportRequest> findRequestById(final String id) {
        return ReactiveMongo.requiredOptional(findByIdOptional(id), () -> {
            LOGGER.error("Unable to find SupportRequest with ID: {}", id);
            return new IllegalStateException("Unable to find SupportRequest with ID: " + id);
        });
    }

    public Uni<List<SupportRequest>> findAllRequests() {
        return mongoCollection()
                .find(new FindOptions().sort(Sorts.descending("createdOnDate")))
                .collect().asList();
    }

    public Uni<SupportRequest> updateRequest(final SupportRequest request) {
        Bson update = Updates.combine(
                Updates.set("title", request.getTitle()),
                Updates.set("description", request.getDescription()),
                Updates.set("contactEmail", request.getContactEmail()),
                Updates.set("priority", request.getPriority()),
                Updates.set("type", request.getType()));
        return ReactiveMongo.modified(
                mongoCollection().updateOne(Filters.eq("_id", request.getId()), update),
                () -> request,
                () -> {
                    LOGGER.error("Unable to update SupportRequest: {}", request.getId());
                    return new IllegalStateException("Unable to update SupportRequest: " + request.getId());
                });
    }

    public Uni<SupportRequest> updateStatus(final String id, final SupportStatus status, final Instant changeDate) {
        Bson update = Updates.combine(
                Updates.set("status", status),
                Updates.set("lastStatusChangeDate", changeDate));
        return mongoCollection().updateOne(Filters.eq("_id", id), update)
                .chain(result -> {
                    if (result.getModifiedCount() == 0) {
                        LOGGER.error("Unable to update status for SupportRequest: {}", id);
                        return Uni.createFrom().failure(
                                new IllegalStateException("Unable to update status for SupportRequest: " + id));
                    }
                    LOGGER.info("Successfully updated status for SupportRequest: {} → {}", id, status);
                    return findRequestById(id);
                });
    }

    public Uni<Boolean> hardDeleteRequest(final String id) {
        return ReactiveMongo.deleted(mongoCollection().deleteOne(Filters.eq("_id", id)))
                .invoke(deleted -> {
                    if (deleted) {
                        LOGGER.info("Hard deleted SupportRequest: {}", id);
                    } else {
                        LOGGER.warn("SupportRequest not found for hard delete: {}", id);
                    }
                });
    }
}
