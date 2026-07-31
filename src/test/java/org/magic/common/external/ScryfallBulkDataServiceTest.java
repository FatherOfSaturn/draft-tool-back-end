package org.magic.common.external;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.magic.common.api.scryfall.BulkDataEntry;
import org.magic.pyramidDraft.app.GameCoordination.MongoService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

class ScryfallBulkDataServiceTest {

    ScryfallCardCache cardCache;
    MongoService mongoService;
    ObjectMapper objectMapper = new ObjectMapper();
    ManagedExecutor executor;
    ScryfallBulkDataService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        cardCache = mock(ScryfallCardCache.class);
        mongoService = mock(MongoService.class);
        executor = mock(ManagedExecutor.class);
        MongoDatabase db = mock(MongoDatabase.class);
        MongoCollection<Document> metaCollection = mock(MongoCollection.class);
        FindIterable<Document> findIterable = mock(FindIterable.class);
        when(mongoService.getDatabase()).thenReturn(db);
        when(db.getCollection("ScryfallCacheMeta")).thenReturn(metaCollection);
        when(metaCollection.find(any(Bson.class))).thenReturn(findIterable);
        service = new ScryfallBulkDataService(true, "default_cards", cardCache, mongoService, objectMapper, executor);
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

        BulkDataEntry result = service.findTargetEntry(entries);

        assertNull(result);
    }

    @Test
    void shouldReturnNullForNullEntries() {
        assertNull(service.findTargetEntry(null));
    }

    @Test
    void shouldReturnStatusFromCache() {
        when(cardCache.isAvailable()).thenReturn(true);
        when(cardCache.count()).thenReturn(50000L);

        var status = service.getStatus();

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
        var disabled = new ScryfallBulkDataService(false, "oracle_cards", cardCache, mongoService, objectMapper, executor);
        assertFalse(disabled.isEnabled());
        assertEquals("oracle_cards", disabled.getBulkDataType());
    }

    @Test
    void shouldThrowWhenTriggeredWithCacheDisabled() {
        var disabled = new ScryfallBulkDataService(false, "oracle_cards", cardCache, mongoService, objectMapper, executor);
        assertThrows(IllegalStateException.class, disabled::triggerRefresh);
    }
}
