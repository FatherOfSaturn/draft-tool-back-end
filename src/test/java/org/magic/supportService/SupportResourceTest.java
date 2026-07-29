package org.magic.supportService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.supportService.api.CreateSupportRequest;
import org.magic.supportService.api.SupportPriority;
import org.magic.supportService.api.SupportRequest;
import org.magic.supportService.api.SupportStatus;
import org.magic.supportService.api.SupportType;
import org.magic.supportService.api.UpdateSupportRequest;
import org.magic.supportService.app.SupportWorker;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class SupportResourceTest {

    @Mock
    SupportWorker supportWorker;

    SupportResource resource;

    @BeforeEach
    void setup() {
        resource = new SupportResource(supportWorker);
    }

    @Test
    void shouldCreateRequest() {
        var request = new CreateSupportRequest("Subject", "Description", "test@example.com", SupportPriority.HIGH, null, SupportType.NEW_FEATURE);
        var expected = new SupportRequest("id123", "Subject", "Description", "test@example.com", SupportPriority.HIGH, null, SupportStatus.NEW, SupportType.NEW_FEATURE, Instant.now(), null);

        when(supportWorker.createRequest(request)).thenReturn(Uni.createFrom().item(expected));

        SupportRequest result = resource.createRequest(request).await().indefinitely();

        assertNotNull(result);
        assertEquals("id123", result.getId());
    }

    @Test
    void shouldGetRequestWhenFound() {
        var expected = new SupportRequest("id123", "Subject", "Desc", "test@example.com", SupportPriority.HIGH, null, SupportStatus.NEW, SupportType.NEW_FEATURE, Instant.now(), null);

        when(supportWorker.getRequest("id123")).thenReturn(Uni.createFrom().item(expected));

        SupportRequest result = resource.getRequest("id123").await().indefinitely();

        assertNotNull(result);
        assertEquals("id123", result.getId());
    }

    @Test
    void shouldThrowNotFoundWhenGettingMissingRequest() {
        when(supportWorker.getRequest("missing")).thenReturn(Uni.createFrom().item((SupportRequest) null));

        assertThrows(WebApplicationException.class, () -> resource.getRequest("missing").await().indefinitely());
    }

    @Test
    void shouldGetAllRequests() {
        var requests = List.of(
                new SupportRequest("id1", "S1", "D1", "a@b.com", SupportPriority.HIGH, null, SupportStatus.NEW, SupportType.NEW_FEATURE, Instant.now(), null),
                new SupportRequest("id2", "S2", "D2", "c@d.com", SupportPriority.CRITICAL, null, SupportStatus.IN_PROGRESS, SupportType.BUG_FIX, Instant.now(), null)
        );

        when(supportWorker.getAllRequests()).thenReturn(Uni.createFrom().item(requests));

        List<SupportRequest> result = resource.getAllRequests().await().indefinitely();

        assertEquals(2, result.size());
    }

    @Test
    void shouldUpdateRequestWhenFound() {
        var update = new UpdateSupportRequest("New Subject", "New Desc", "new@email.com", SupportPriority.LOW, SupportType.BUG_FIX);
        var updated = new SupportRequest("id123", "New Subject", "New Desc", "new@email.com", SupportPriority.LOW, null, SupportStatus.NEW, SupportType.BUG_FIX, Instant.now(), null);

        when(supportWorker.updateRequest("id123", update)).thenReturn(Uni.createFrom().item(updated));

        SupportRequest result = resource.updateRequest("id123", update).await().indefinitely();

        assertEquals("New Subject", result.getTitle());
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingMissingRequest() {
        when(supportWorker.updateRequest(any(), any())).thenReturn(Uni.createFrom().item((SupportRequest) null));

        assertThrows(WebApplicationException.class,
                () -> resource.updateRequest("missing", new UpdateSupportRequest("S", "D", "e@m.com", SupportPriority.MEDIUM, SupportType.MISC_SUPPORT)).await().indefinitely());
    }

    @Test
    void shouldUpdateStatusWhenFound() {
        var updated = new SupportRequest("id123", "Subject", "Desc", "test@example.com", SupportPriority.HIGH, null, SupportStatus.COMPLETED, SupportType.NEW_FEATURE, Instant.now(), Instant.now());

        when(supportWorker.updateStatus("id123", SupportStatus.COMPLETED)).thenReturn(Uni.createFrom().item(updated));

        SupportRequest result = resource.updateStatus("id123", new SupportResource.UpdateStatusRequest(SupportStatus.COMPLETED)).await().indefinitely();

        assertEquals(SupportStatus.COMPLETED, result.getStatus());
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingStatusOnMissingRequest() {
        when(supportWorker.updateStatus(any(), any())).thenReturn(Uni.createFrom().item((SupportRequest) null));

        assertThrows(WebApplicationException.class,
                () -> resource.updateStatus("missing", new SupportResource.UpdateStatusRequest(SupportStatus.NEW)).await().indefinitely());
    }

    @Test
    void shouldDeleteRequestWhenFound() {
        when(supportWorker.deleteRequest("id123")).thenReturn(Uni.createFrom().item(true));

        Response response = resource.deleteRequest("id123").await().indefinitely();

        assertEquals(204, response.getStatus());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingRequest() {
        when(supportWorker.deleteRequest("missing")).thenReturn(Uni.createFrom().item(false));

        Response response = resource.deleteRequest("missing").await().indefinitely();

        assertEquals(404, response.getStatus());
    }

    @Test
    void shouldRejectNullStatusInUpdateStatusRequest() {
        assertThrows(IllegalArgumentException.class, () -> new SupportResource.UpdateStatusRequest(null));
    }
}
