package org.magic.supportService.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.supportService.api.SupportPriority;
import org.magic.supportService.api.SupportRequest;
import org.magic.supportService.api.SupportStatus;
import org.magic.supportService.api.SupportType;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class SupportDbHandlerTest {

    @Mock
    ReactiveMongoCollection<SupportRequest> collection;

    SupportDbHandler handler;

    private final Instant now = Instant.now();

    @BeforeEach
    void setup() {
        handler = spy(new SupportDbHandler());
        doReturn(collection).when(handler).mongoCollection();
    }

    private SupportRequest createRequest(String id) {
        return new SupportRequest(id, "Title", "Desc", "email@test.com", SupportPriority.MEDIUM,
                null, SupportStatus.NEW, SupportType.NEW_FEATURE, now, null);
    }

    @Test
    void shouldAddRequest() {
        var request = createRequest("id-1");
        when(collection.insertOne(any(SupportRequest.class)))
                .thenReturn(Uni.createFrom().item(
                        InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        handler.addRequest(request).await().indefinitely();

        verify(collection).insertOne(request);
    }

    @Test
    void shouldFindById() {
        var request = createRequest("id-1");
        when(collection.find(any(Bson.class))).thenReturn(Multi.createFrom().item(request));

        SupportRequest result = handler.findById("id-1").await().indefinitely();

        assertNotNull(result);
        assertEquals("id-1", result.getId());
    }

    @Test
    void shouldThrowWhenFindByIdNotFound() {
        when(collection.find(any(Bson.class))).thenReturn(Multi.createFrom().empty());

        assertThrows(IllegalStateException.class,
                () -> handler.findById("missing").await().indefinitely());
    }

    @Test
    void shouldFindAll() {
        when(collection.find(any(io.quarkus.mongodb.FindOptions.class)))
                .thenReturn(Multi.createFrom().items(createRequest("id-1"), createRequest("id-2")));

        List<SupportRequest> result = handler.findAllRequests().await().indefinitely();

        assertEquals(2, result.size());
    }

    @Test
    void shouldUpdateRequest() {
        var request = createRequest("id-1");
        when(collection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Uni.createFrom().item(UpdateResult.acknowledged(1L, 1L, null)));

        SupportRequest result = handler.updateRequest(request).await().indefinitely();

        assertEquals("id-1", result.getId());
    }

    @Test
    void shouldThrowWhenUpdateRequestFails() {
        var request = createRequest("id-1");
        when(collection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Uni.createFrom().item(UpdateResult.acknowledged(1L, 0L, null)));

        assertThrows(IllegalStateException.class,
                () -> handler.updateRequest(request).await().indefinitely());
    }

    @Test
    void shouldUpdateStatus() {
        var request = createRequest("id-1");
        when(collection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Uni.createFrom().item(UpdateResult.acknowledged(1L, 1L, null)));
        when(collection.find(any(Bson.class))).thenReturn(Multi.createFrom().item(request));

        SupportRequest result = handler.updateStatus("id-1", SupportStatus.COMPLETED, now)
                .await().indefinitely();

        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenUpdateStatusFails() {
        when(collection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Uni.createFrom().item(UpdateResult.acknowledged(1L, 0L, null)));

        assertThrows(IllegalStateException.class,
                () -> handler.updateStatus("id-1", SupportStatus.COMPLETED, now).await().indefinitely());
    }

    @Test
    void shouldHardDeleteRequest() {
        when(collection.deleteOne(any(Bson.class)))
                .thenReturn(Uni.createFrom().item(DeleteResult.acknowledged(1)));

        assertTrue(handler.hardDeleteRequest("id-1").await().indefinitely());
    }

    @Test
    void shouldReturnFalseWhenHardDeletingMissingRequest() {
        when(collection.deleteOne(any(Bson.class)))
                .thenReturn(Uni.createFrom().item(DeleteResult.acknowledged(0)));

        assertFalse(handler.hardDeleteRequest("missing").await().indefinitely());
    }
}
