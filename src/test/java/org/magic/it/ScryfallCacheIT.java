package org.magic.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.magic.TestJson;
import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.external.ScryfallCardCache;
import org.magic.common.external.ScryfallCardService;

import com.mongodb.client.model.Filters;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/**
 * End-to-end test of the Scryfall card cache against a real MongoDB: proves the
 * record-entity Panache repository maps documents exactly like the production
 * queries expect (fields {@code id} and {@code name} stay regular fields, the
 * driver-owned {@code _id} is present), and that the service really serves
 * reads from the cache without calling the Scryfall API.
 */
@QuarkusTest
@TestProfile(ScryfallCacheIT.CacheProfile.class)
class ScryfallCacheIT {

    /** Enables the cache and points the Scryfall client at the local stub. */
    public static class CacheProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    // Unreachable on purpose: any accidental Scryfall call fails the test.
                    "scryfallApi/mp-rest/url", "http://127.0.0.1:1",
                    "scryfall.cache.enabled", "true",
                    "scryfall.cache.bulk-data-type", "default_cards");
        }
    }

    private static ScryfallCard lightningBolt;
    private static ScryfallCard brisela;

    @Inject
    ScryfallCardCache cache;

    @Inject
    ScryfallCardService cardService;

    @Inject
    ReactiveMongoClient mongoClient;

    @BeforeAll
    static void loadFixtures() throws Exception {
        lightningBolt = TestJson.fromJson(
                new String(ScryfallCacheIT.class.getClassLoader()
                        .getResourceAsStream("ScryfallLightningBolt.json").readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8), ScryfallCard.class);
        brisela = TestJson.fromJson(
                new String(ScryfallCacheIT.class.getClassLoader()
                        .getResourceAsStream("ScryfallBrisela.json").readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8), ScryfallCard.class);
    }

    @BeforeEach
    void populateCache() {
        cache.dropCollection().await().indefinitely();
        cache.createIndexes().await().indefinitely();
        cache.bulkInsert(List.of(lightningBolt, brisela)).await().indefinitely();
    }

    @AfterEach
    void clearCache() {
        cache.dropCollection().await().indefinitely();
    }

    @Test
    void documentShapeKeepsSearchableFields() {
        ReactiveMongoCollection<Document> raw =
                mongoClient.getDatabase("MTGames").getCollection("ScryfallCards");

        Document doc = raw.find(Filters.eq("id", lightningBolt.id())).collect().first()
                .await().indefinitely();

        assertNotNull(doc, "document must be findable by the 'id' field used in production queries");
        assertEquals(lightningBolt.id(), doc.getString("id"));
        assertEquals("Lightning Bolt", doc.getString("name"));
        assertNotNull(doc.get("_id"), "driver _id must still be present");
        // The document is a faithful materialization of the DTO: fields are stored
        // under their Java component names (driver record codec), and queries only
        // rely on "id"/"name", which are verified above.
    }

    @Test
    void cacheLookupsWorkThroughRepository() {
        assertEquals(2L, cache.count().await().indefinitely());
        assertTrue(cache.isAvailable().await().indefinitely());

        ScryfallCard byId = cache.findById(lightningBolt.id()).await().indefinitely();
        assertNotNull(byId);
        // Full structural round-trip: every component (mana cost, images, faces,
        // legalities, ...) survives the Mongo write/read cycle unchanged.
        assertEquals(lightningBolt, byId);

        ScryfallCard byName = cache.findByName("Lightning Bolt").await().indefinitely();
        assertNotNull(byName);
        assertEquals(lightningBolt.id(), byName.id());

        List<ScryfallCard> byNames = cache.findByNames(List.of("Lightning Bolt", "Brisela, Voice of Nightmares"))
                .await().indefinitely();
        assertEquals(2, byNames.size());
    }

    @Test
    void serviceServesSingleCardFromCacheWithoutHttp() {
        ScryfallCard card = cardService.getCardById(lightningBolt.id()).await().indefinitely();

        assertNotNull(card);
        assertEquals("Lightning Bolt", card.name());
        // No counter needed: the Scryfall URL is unreachable in this profile, so a
        // cache miss would have failed instead of returning the card.
    }

    @Test
    void serviceServesBatchFromCacheWithoutHttp() {
        var result = cardService.batchLookupByNames(List.of("Lightning Bolt", "Brisela, Voice of Nightmares"))
                .await().indefinitely();

        assertEquals(2, result.cards().size());
        assertTrue(result.notFound().isEmpty());

        assertFalse(result.cards().stream()
                .anyMatch(card -> card.getCardDetails() == null), "converted cards must be complete");
    }
}
