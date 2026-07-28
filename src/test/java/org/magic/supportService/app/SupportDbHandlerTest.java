package org.magic.supportService.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.pyramidDraft.app.GameCoordination.MongoService;
import org.magic.supportService.api.SupportPriority;
import org.magic.supportService.api.SupportRequest;
import org.magic.supportService.api.SupportStatus;
import org.magic.supportService.api.SupportType;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

@ExtendWith(MockitoExtension.class)
class SupportDbHandlerTest {

    @Mock
    MongoService mongoService;
    @Mock
    MongoDatabase database;
    @Mock
    MongoCollection<SupportRequest> collection;
    @Mock
    FindIterable<SupportRequest> findIterable;
    @Mock
    UpdateResult updateResult;
    @Mock
    DeleteResult deleteResult;

    SupportDbHandler handler;

    private final Instant now = Instant.now();

    @BeforeEach
    void setup() {
        when(mongoService.getDatabase()).thenReturn(database);
        when(database.getCollection("SupportRequests", SupportRequest.class)).thenReturn(collection);
        handler = new SupportDbHandler();
        handler.mongoService = mongoService;
    }

    private SupportRequest createRequest(String id) {
        return new SupportRequest(id, "Title", "Desc", "email@test.com", SupportPriority.MEDIUM, null, SupportStatus.NEW, SupportType.NEW_FEATURE, now, null);
    }

    @Test
    void shouldAddRequest() {
        var request = createRequest("id-1");
        handler.addRequest(request);
        verify(collection).insertOne(request);
    }

    @Test
    void shouldFindById() {
        var request = createRequest("id-1");

        when(collection.find(any(Bson.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(request);

        SupportRequest result = handler.findById("id-1");

        assertNotNull(result);
        assertEquals("id-1", result.getId());
    }

    @Test
    void shouldThrowWhenFindByIdNotFound() {
        when(collection.find(any(Bson.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> handler.findById("missing"));
    }

    @Test
    void shouldFindAll() {
        var requests = List.of(createRequest("id-1"), createRequest("id-2"));

        when(collection.find()).thenReturn(findIterable);
        when(findIterable.sort(any())).thenReturn(findIterable);
        when(findIterable.into(any())).thenReturn(requests);

        List<SupportRequest> result = handler.findAll();

        assertEquals(2, result.size());
    }

    @Test
    void shouldUpdateRequest() {
        var request = createRequest("id-1");

        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(1L);

        SupportRequest result = handler.updateRequest(request);

        assertEquals("id-1", result.getId());
    }

    @Test
    void shouldThrowWhenUpdateRequestFails() {
        var request = createRequest("id-1");

        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> handler.updateRequest(request));
    }

    @Test
    void shouldUpdateStatus() {
        var request = createRequest("id-1");

        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(collection.find(any(Bson.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(request);

        SupportRequest result = handler.updateStatus("id-1", SupportStatus.COMPLETED, now);

        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenUpdateStatusFails() {
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> handler.updateStatus("id-1", SupportStatus.COMPLETED, now));
    }

    @Test
    void shouldHardDeleteRequest() {
        when(collection.deleteOne(any(Bson.class))).thenReturn(deleteResult);
        when(deleteResult.getDeletedCount()).thenReturn(1L);

        boolean result = handler.hardDeleteRequest("id-1");

        assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenHardDeletingMissingRequest() {
        when(collection.deleteOne(any(Bson.class))).thenReturn(deleteResult);
        when(deleteResult.getDeletedCount()).thenReturn(0L);

        boolean result = handler.hardDeleteRequest("missing");

        assertFalse(result);
    }
}
