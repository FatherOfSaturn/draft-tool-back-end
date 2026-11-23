package org.magic.supportService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.common.ClientIpResolver;
import org.magic.supportService.api.CreateSupportAck;
import org.magic.supportService.api.CreateSupportRequest;
import org.magic.supportService.api.FormTokenResponse;
import org.magic.supportService.api.PublicSupportRequest;
import org.magic.supportService.api.SupportPriority;
import org.magic.supportService.api.SupportRequest;
import org.magic.supportService.api.SupportStatus;
import org.magic.supportService.api.SupportType;
import org.magic.supportService.api.UpdateSupportRequest;
import org.magic.supportService.app.FormTokenService;
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

    @Mock
    FormTokenService formTokenService;

    @Mock
    ClientIpResolver clientIpResolver;

    SupportResource resource;

    @BeforeEach
    void setup() {
        resource = new SupportResource(supportWorker, formTokenService, clientIpResolver);
    }

    private CreateSupportRequest createRequest() {
        return new CreateSupportRequest("Subject", "Description", "test@example.com",
                SupportPriority.HIGH, null, null, null, SupportType.NEW_FEATURE);
    }

    @Test
    void shouldCreateRequestReturningMinimalAck() {
        var request = createRequest();
        var expected = new SupportRequest("id123", "Subject", "Description", "test@example.com",
                SupportPriority.HIGH, null, SupportStatus.NEW, SupportType.NEW_FEATURE, Instant.now(), null);

        when(supportWorker.createRequest(request, "203.0.113.5")).thenReturn(Uni.createFrom().item(expected));
        when(clientIpResolver.fromRoutingContext(null)).thenReturn("203.0.113.5");

        CreateSupportAck result = resource.createRequest(request, null).await().indefinitely();

        assertNotNull(result);
        assertEquals("id123", result.id());
    }

    @Test
    void shouldIssueFormToken() {
        when(formTokenService.issue()).thenReturn("123.abc");

        FormTokenResponse result = resource.issueFormToken();

        assertEquals("123.abc", result.token());
    }

    @Test
    void shouldGetRequestWhenFound() {
        var expected = new SupportRequest("id123", "Subject", "Desc", "test@example.com", SupportPriority.HIGH, "acct-9", SupportStatus.NEW, SupportType.NEW_FEATURE, Instant.now(), null);

        when(supportWorker.getRequest("id123")).thenReturn(Uni.createFrom().item(expected));

        PublicSupportRequest result = resource.getRequest("id123").await().indefinitely();

        assertNotNull(result);
        assertEquals("id123", result.id());
        assertEquals("Subject", result.title());
        assertEquals(SupportPriority.HIGH, result.priority());
        assertEquals(SupportStatus.NEW, result.status());
        assertEquals(SupportType.NEW_FEATURE, result.type());
    }

    @Test
    void shouldNotExposePiiInPublicGet() {
        var expected = new SupportRequest("id123", "Subject", "Desc", "test@example.com", SupportPriority.HIGH, "acct-9", SupportStatus.NEW, SupportType.NEW_FEATURE, Instant.now(), null);

        when(supportWorker.getRequest("id123")).thenReturn(Uni.createFrom().item(expected));

        PublicSupportRequest result = resource.getRequest("id123").await().indefinitely();

        assertFalse(result.toString().contains("test@example.com"));
        assertFalse(result.toString().contains("acct-9"));
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
    void shouldListPublicRequestsExcludingDeletedAndQuarantined() {
        var requests = List.of(
                new SupportRequest("id1", "S1", "D1", "a@b.com", SupportPriority.HIGH, null, SupportStatus.NEW, SupportType.NEW_FEATURE, Instant.now(), null),
                new SupportRequest("id2", "S2", "D2", "c@d.com", SupportPriority.CRITICAL, null, SupportStatus.DELETED, SupportType.BUG_FIX, Instant.now(), null),
                new SupportRequest("id3", "S3", "D3", "e@f.com", SupportPriority.LOW, null, SupportStatus.QUARANTINED, SupportType.MISC_SUPPORT, Instant.now(), null)
        );

        when(supportWorker.getAllRequests()).thenReturn(Uni.createFrom().item(requests));

        List<PublicSupportRequest> result = resource.getPublicRequests().await().indefinitely();

        assertEquals(1, result.size());
        assertEquals("id1", result.get(0).id());
    }

    @Test
    void shouldNotExposePiiInPublicList() {
        var requests = List.of(
                new SupportRequest("id1", "S1", "D1", "a@b.com", SupportPriority.HIGH, "acct-9", SupportStatus.NEW, SupportType.NEW_FEATURE, Instant.now(), null)
        );

        when(supportWorker.getAllRequests()).thenReturn(Uni.createFrom().item(requests));

        List<PublicSupportRequest> result = resource.getPublicRequests().await().indefinitely();

        assertFalse(result.toString().contains("a@b.com"));
        assertFalse(result.toString().contains("acct-9"));
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
