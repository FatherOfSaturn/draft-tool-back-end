package org.magic.common.external;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.magic.common.api.scryfall.ScryfallCard;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertManyOptions;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ScryfallCardCache {
    private static final Logger LOGGER = LogManager.getLogger(ScryfallCardCache.class);
    private static final String COLLECTION = "ScryfallCards";

    /**
     * POJO codec registry, matching what the (now removed) MongoService
     * configured: the default registry plus automatic POJO support.
     */
    private static final CodecRegistry POJO_REGISTRY = fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    private final MongoCollection<ScryfallCard> collection;

    @Inject
    public ScryfallCardCache(final MongoClient mongoClient,
                             @ConfigProperty(name = "quarkus.mongodb.database") final String databaseName) {
        this.collection = mongoClient.getDatabase(databaseName)
                .withCodecRegistry(POJO_REGISTRY)
                .getCollection(COLLECTION, ScryfallCard.class);
    }

    private MongoCollection<ScryfallCard> getCollection() {
        return collection;
    }

    public ScryfallCard findByName(final String name) {
        return getCollection().find(Filters.eq("name", name)).first();
    }

    public List<ScryfallCard> findByNames(final List<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        return getCollection().find(Filters.in("name", names)).into(new ArrayList<>());
    }

    public ScryfallCard findById(final String id) {
        return getCollection().find(Filters.eq("id", id)).first();
    }

    public long count() {
        return getCollection().countDocuments();
    }

    public boolean isAvailable() {
        return count() > 0;
    }

    public void dropCollection() {
        getCollection().drop();
    }

    public void bulkInsert(final List<ScryfallCard> cards) {
        if (cards == null || cards.isEmpty()) return;
        getCollection().insertMany(cards, new InsertManyOptions().ordered(false));
    }

    public void createIndexes() {
        MongoCollection<ScryfallCard> col = getCollection();
        col.createIndex(Indexes.ascending("id"));
        col.createIndex(Indexes.ascending("name"));
        LOGGER.info("ScryfallCardCache indexes ensured");
    }
}
