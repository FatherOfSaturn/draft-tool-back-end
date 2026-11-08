package org.magic.common.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.magic.common.api.scryfall.BulkDataEntry;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.quarkus.mongodb.reactive.ReactiveMongoDatabase;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

class ScryfallBulkDataServiceTest {

    ScryfallCardCache cardCache;
    ReactiveMongoClient mongoClient;
    ObjectMapper objectMapper = new ObjectMapper();
    ManagedExecutor executor;
    ScryfallBulkDataService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        cardCache = mock(ScryfallCardCache.class);
        mongoClient = mock(ReactiveMongoClient.class);
        executor = mock(ManagedExecutor.class);
        ReactiveMongoDatabase db = mock(ReactiveMongoDatabase.class);
        ReactiveMongoCollection<Document> metaCollection = mock(ReactiveMongoCollection.class);
        when(mongoClient.getDatabase("MTGames")).thenReturn(db);
        when(db.getCollection("ScryfallCacheMeta")).thenReturn(metaCollection);
        when(metaCollection.find(any(Bson.class))).thenReturn(Multi.createFrom().empty());
        service = new ScryfallBulkDataService(true, "default_cards", cardCache, mongoClient, "MTGames",
                objectMapper, executor);
    }

    @Test
    void shouldFindTargetEntryByType() {
        var entries = List.of(
                new BulkDataEntry("id1", "oracle_cards", "2026-07-29T21:02:49.097+00:00", "", "Oracle Cards", "", "", 0),
                new BulkDataEntry("id2", "default_cards", "2026-07-29T21:10:03.341+00:00", "", "Default Cards", "", "", 77046451),
                new BulkDataEntry("id3", "all_cards", "2026-07-29T21:27:56.280+00:00", "", "All Cards", "", "", 389772467));

        BulkDataEntry result = service.findTargetEntry(entries);

        assertNotNull(result);
        assertEquals("default_cards", result.type());
        assertEquals("Default Cards", result.name());
        assertEquals(77046451, result.compressedSize());
    }

    @Test
    void shouldReturnNullWhenNoMatchingEntry() {
        var entries = List.of(
                new BulkDataEntry("id1", "oracle_cards", "", "", "Oracle Cards", "", "", 0));

        assertNull(service.findTargetEntry(entries));
    }

    @Test
    void shouldReturnNullForNullEntries() {
        assertNull(service.findTargetEntry(null));
    }

    @Test
    void shouldReturnStatusFromCache() {
        when(cardCache.isAvailable()).thenReturn(Uni.createFrom().item(true));
        when(cardCache.count()).thenReturn(Uni.createFrom().item(50000L));

        var status = service.getStatus().await().indefinitely();

        assertTrue(status.available());
        assertEquals(50000, status.cardCount());
        assertEquals("default_cards", status.bulkDataType());
    }

    @Test
    void shouldReturnEnabledStatus() {
        assertTrue(service.isEnabled());
        assertEquals("default_cards", service.getBulkDataType());
    }

    @Test
    void shouldReturnDisabledStatus() {
        var disabled = new ScryfallBulkDataService(false, "oracle_cards", cardCache, mongoClient, "MTGames",
                objectMapper, executor);
        assertFalse(disabled.isEnabled());
        assertEquals("oracle_cards", disabled.getBulkDataType());
    }

    @Test
    void shouldRejectTriggerWhenCacheDisabled() {
        var disabled = new ScryfallBulkDataService(false, "oracle_cards", cardCache, mongoClient, "MTGames",
                objectMapper, executor);
        assertThrows(IllegalStateException.class,
                () -> disabled.triggerRefresh().await().indefinitely());
    }
}
