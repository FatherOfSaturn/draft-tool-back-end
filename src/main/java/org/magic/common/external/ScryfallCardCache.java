package org.magic.common.external;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.conversions.Bson;
import org.magic.common.api.scryfall.ScryfallCard;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertManyOptions;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive cache of Scryfall cards, backed by the {@code ScryfallCards}
 * collection. Used by the bulk-data import and by the request path to avoid
 * hitting the Scryfall API.
 */
@ApplicationScoped
public class ScryfallCardCache implements ReactivePanacheMongoRepository<ScryfallCard> {
    private static final Logger LOGGER = LogManager.getLogger(ScryfallCardCache.class);

    public Uni<ScryfallCard> findByName(final String name) {
        return mongoCollection().find(Filters.eq("name", name)).collect().first();
    }

    public Uni<List<ScryfallCard>> findByNames(final List<String> names) {
        if (names == null || names.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        Bson filter = Filters.in("name", names);
        return mongoCollection().find(filter).collect().asList();
    }

    public Uni<ScryfallCard> findById(final String id) {
        return mongoCollection().find(Filters.eq("id", id)).collect().first();
    }

    public Uni<Long> count() {
        return mongoCollection().countDocuments();
    }

    /**
     * Returns whether the cache has been populated. Used to decide between the
     * cache and the Scryfall API.
     */
    public Uni<Boolean> isAvailable() {
        return count().map(populated -> populated > 0);
    }

    public Uni<Void> dropCollection() {
        return mongoCollection().drop().replaceWithVoid();
    }

    public Uni<Void> bulkInsert(final List<ScryfallCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        return mongoCollection().insertMany(cards, new InsertManyOptions().ordered(false)).replaceWithVoid();
    }

    public Uni<Void> createIndexes() {
        return mongoCollection().createIndex(Indexes.ascending("id"))
                .chain(ignored -> mongoCollection().createIndex(Indexes.ascending("name")))
                .invoke(ignored -> LOGGER.info("ScryfallCardCache indexes ensured"))
                .replaceWithVoid();
    }
}
