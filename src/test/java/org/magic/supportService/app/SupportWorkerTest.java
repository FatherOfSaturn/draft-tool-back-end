package org.magic.supportService.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.supportService.api.CreateSupportRequest;
import org.magic.supportService.api.SupportRequest;
import org.magic.supportService.api.SupportStatus;
import org.magic.supportService.api.SupportType;
import org.magic.supportService.api.SupportPriority;
import org.magic.supportService.api.UpdateSupportRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class SupportWorkerTest {

    @Mock
    SupportDbHandler supportDbHandler;

    @Mock
    AntiSpamService antiSpamService;

    SupportWorker supportWorker;

    String testId = "507f1f77bcf86cd799439011";

    @BeforeEach
    void setup() {
        supportWorker = new SupportWorker(supportDbHandler, antiSpamService);
    }

    private CreateSupportRequest createRequest(final String description) {
        return new CreateSupportRequest(
                "Add dark mode", description, "user@example.com",
                SupportPriority.HIGH, null, null, null, SupportType.NEW_FEATURE);
    }

    @Test
    void shouldCreateRequest() {
        CreateSupportRequest request = createRequest("Support dark mode theme");
        when(antiSpamService.isHoneypot(request)).thenReturn(false);
        when(antiSpamService.prePersistChecks(any(), any())).thenReturn(Uni.createFrom().voidItem());
        when(supportDbHandler.addRequest(any(SupportRequest.class)))
                .thenReturn(Uni.createFrom().voidItem());

        SupportRequest result = supportWorker.createRequest(request, "203.0.113.5").await().indefinitely();

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("Add dark mode", result.getTitle());
        assertEquals(SupportStatus.NEW, result.getStatus());
        assertNotNull(result.getCreatedOnDate());
        assertNotNull(result.getLastStatusChangeDate());
        verify(supportDbHandler).addRequest(any(SupportRequest.class));
    }

    @Test
    void shouldNeverPersistClientSuppliedAccountID() {
        CreateSupportRequest request = createRequest("Support dark mode theme");
        when(antiSpamService.isHoneypot(request)).thenReturn(false);
        when(antiSpamService.prePersistChecks(any(), any())).thenReturn(Uni.createFrom().voidItem());
        when(supportDbHandler.addRequest(any(SupportRequest.class)))
                .thenReturn(Uni.createFrom().voidItem());

        SupportRequest result = supportWorker.createRequest(request, "203.0.113.5").await().indefinitely();

        assertNull(result.getAccountID(), "accountID must never come from the client");
    }

    @Test
    void shouldRunAntiSpamChecksBeforePersisting() {
        CreateSupportRequest request = createRequest("Support dark mode theme");
        when(antiSpamService.isHoneypot(request)).thenReturn(false);
        when(antiSpamService.prePersistChecks(eq(request), eq("203.0.113.5")))
                .thenReturn(Uni.createFrom().voidItem());
        when(supportDbHandler.addRequest(any(SupportRequest.class)))
                .thenReturn(Uni.createFrom().voidItem());

        supportWorker.createRequest(request, "203.0.113.5").await().indefinitely();

        var inOrder = org.mockito.Mockito.inOrder(antiSpamService, supportDbHandler);
        inOrder.verify(antiSpamService).prePersistChecks(eq(request), eq("203.0.113.5"));
        inOrder.verify(antiSpamService).checkEmailRate("user@example.com");
        inOrder.verify(supportDbHandler).addRequest(any(SupportRequest.class));
    }

    @Test
    void shouldHoneypotReturnFakeAckWithoutPersisting() {
        CreateSupportRequest request = new CreateSupportRequest(
                "Buy v1agra", "cheap deals w1th fr33 sh1pp1ng", "bot@spam.com",
                SupportPriority.LOW, "http://spam.example", null, null, SupportType.MISC_SUPPORT);
        when(antiSpamService.isHoneypot(request)).thenReturn(true);

        SupportRequest result = supportWorker.createRequest(request, "203.0.113.5").await().indefinitely();

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(SupportStatus.NEW, result.getStatus());
        verify(supportDbHandler, never()).addRequest(any(SupportRequest.class));
        verify(antiSpamService, never()).prePersistChecks(any(), any());
        verify(antiSpamService, never()).checkEmailRate(any());
    }

    @Test
    void shouldQuarantineSpamLookingDescription() {
        StringBuilder gibberish = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            gibberish.append("xkcdqwryzptvbnmjhgfe");
        }
        CreateSupportRequest request = createRequest(gibberish.toString());
        when(antiSpamService.isHoneypot(request)).thenReturn(false);
        when(antiSpamService.prePersistChecks(any(), any())).thenReturn(Uni.createFrom().voidItem());
        when(supportDbHandler.addRequest(any(SupportRequest.class)))
                .thenReturn(Uni.createFrom().voidItem());

        SupportRequest result = supportWorker.createRequest(request, "203.0.113.5").await().indefinitely();

        assertEquals(SupportStatus.QUARANTINED, result.getStatus());
    }

    @Test
    void shouldGetRequest() {
        Instant now = Instant.now();
        SupportRequest expected = new SupportRequest(
                testId, "Title", "Desc", "e@e.com", SupportPriority.HIGH, "acct-1",
                SupportStatus.NEW, SupportType.BUG_FIX, now, now);

        when(supportDbHandler.findRequestById(testId)).thenReturn(Uni.createFrom().item(expected));

        SupportRequest result = supportWorker.getRequest(testId).await().indefinitely();

        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals("Title", result.getTitle());
    }

    @Test
    void shouldGetAllRequests() {
        Instant now = Instant.now();
        List<SupportRequest> expected = List.of(
                new SupportRequest(testId, "Title1", "Desc1", "a@a.com", SupportPriority.HIGH, null,
                        SupportStatus.NEW, SupportType.NEW_FEATURE, now, now),
                new SupportRequest("id2", "Title2", "Desc2", "b@b.com", SupportPriority.LOW, null,
                        SupportStatus.IN_PROGRESS, SupportType.BUG_FIX, now, now)
        );

        when(supportDbHandler.findAllRequests()).thenReturn(Uni.createFrom().item(expected));

        List<SupportRequest> result = supportWorker.getAllRequests().await().indefinitely();

        assertEquals(2, result.size());
        assertEquals("Title1", result.get(0).getTitle());
        assertEquals("Title2", result.get(1).getTitle());
    }

    @Test
    void shouldUpdateRequestFields() {
        Instant now = Instant.now();
        SupportRequest existing = new SupportRequest(
                testId, "Old Title", "Old Desc", "old@e.com", SupportPriority.LOW, "acct-1",
                SupportStatus.NEW, SupportType.BUG_FIX, now, now);

        when(supportDbHandler.findRequestById(testId)).thenReturn(Uni.createFrom().item(existing));
        when(supportDbHandler.updateRequest(any(SupportRequest.class))).thenReturn(Uni.createFrom().item(existing));

        UpdateSupportRequest update = new UpdateSupportRequest("New Title", "New Desc", null, null, SupportType.NEW_FEATURE);

        SupportRequest result = supportWorker.updateRequest(testId, update).await().indefinitely();

        assertNotNull(result);
        verify(supportDbHandler).updateRequest(existing);
    }

    @Test
    void shouldNotOverwriteFieldsWithNullValues() {
        Instant now = Instant.now();
        SupportRequest existing = new SupportRequest(
                testId, "Original Title", "Original Desc", "orig@e.com", SupportPriority.MEDIUM, "acct-1",
                SupportStatus.IN_PROGRESS, SupportType.BUG_FIX, now, now);

        when(supportDbHandler.findRequestById(testId)).thenReturn(Uni.createFrom().item(existing));
        when(supportDbHandler.updateRequest(any(SupportRequest.class))).thenReturn(Uni.createFrom().item(existing));

        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);

        UpdateSupportRequest partial = new UpdateSupportRequest(null, null, null, null, null);
        supportWorker.updateRequest(testId, partial).await().indefinitely();

        verify(supportDbHandler).updateRequest(captor.capture());
        SupportRequest saved = captor.getValue();

        assertEquals("Original Title", saved.getTitle());
        assertEquals("Original Desc", saved.getDescription());
        assertEquals("orig@e.com", saved.getContactEmail());
        assertEquals(SupportPriority.MEDIUM, saved.getPriority());
        assertEquals(SupportType.BUG_FIX, saved.getType());
    }

    @Test
    void shouldUpdateStatus() {
        Instant now = Instant.now();
        SupportRequest existing = new SupportRequest(
                testId, "Title", "Desc", "e@e.com", SupportPriority.HIGH, null,
                SupportStatus.NEW, SupportType.BUG_FIX, now, now);

        when(supportDbHandler.updateStatus(eq(testId), eq(SupportStatus.IN_PROGRESS), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(existing));

        SupportRequest result = supportWorker.updateStatus(testId, SupportStatus.IN_PROGRESS).await().indefinitely();

        assertNotNull(result);
        verify(supportDbHandler).updateStatus(eq(testId), eq(SupportStatus.IN_PROGRESS), any(Instant.class));
    }

    @Test
    void shouldUpdateStatusToQuarantined() {
        Instant now = Instant.now();
        SupportRequest existing = new SupportRequest(
                testId, "Title", "Desc", "e@e.com", SupportPriority.HIGH, null,
                SupportStatus.QUARANTINED, SupportType.MISC_SUPPORT, now, now);

        when(supportDbHandler.updateStatus(eq(testId), eq(SupportStatus.QUARANTINED), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(existing));

        SupportRequest result = supportWorker.updateStatus(testId, SupportStatus.QUARANTINED).await().indefinitely();

        assertEquals(SupportStatus.QUARANTINED, result.getStatus());
    }

    @Test
    void shouldSoftDeleteRequest() {
        Instant now = Instant.now();
        SupportRequest existing = new SupportRequest(
                testId, "Title", "Desc", "e@e.com", SupportPriority.HIGH, null,
                SupportStatus.NEW, SupportType.BUG_FIX, now, now);

        when(supportDbHandler.updateStatus(eq(testId), eq(SupportStatus.DELETED), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(existing));

        SupportRequest result = supportWorker.softDeleteRequest(testId).await().indefinitely();

        assertNotNull(result);
        verify(supportDbHandler).updateStatus(eq(testId), eq(SupportStatus.DELETED), any(Instant.class));
    }

    @Test
    void shouldHardDeleteRequest() {
        when(supportDbHandler.hardDeleteRequest(testId)).thenReturn(Uni.createFrom().item(true));

        Boolean result = supportWorker.deleteRequest(testId).await().indefinitely();

        assertTrue(result);
        verify(supportDbHandler).hardDeleteRequest(testId);
    }

    @Test
    void shouldReturnFalseForHardDeleteOnMissing() {
        String missingId = "507f1f77bcf86cd799439099";
        when(supportDbHandler.hardDeleteRequest(missingId)).thenReturn(Uni.createFrom().item(false));

        Boolean result = supportWorker.deleteRequest(missingId).await().indefinitely();

        assertFalse(result);
    }
}
