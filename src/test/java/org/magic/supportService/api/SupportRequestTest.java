package org.magic.supportService.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.magic.common.util.JsonUtility;

/**
 * Tests for {@link SupportRequest}, {@link SupportStatus}, and {@link SupportType} data classes.
 * Validates serialization, deserialization, and enum parsing.
 */
class SupportRequestTest {

    @Test
    void testSupportRequestDeserialization() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("SupportRequest.json");
        String json = IOUtils.toString(is, "UTF-8");

        SupportRequest request = JsonUtility.getInstance().fromJson(json, SupportRequest.class);

        assertEquals("507f1f77bcf86cd799439011", request.getId());
        assertEquals("Add dark mode support", request.getTitle());
        assertEquals("The application should support a dark mode theme for better accessibility during nighttime use.", request.getDescription());
        assertEquals("user@example.com", request.getContactEmail());
        assertEquals(SupportPriority.HIGH, request.getPriority());
        assertEquals("507f1f77bcf86cd799439099", request.getAccountID());
        assertNotNull(request.getCreatedOnDate());
        assertNotNull(request.getLastStatusChangeDate());
    }

    @Test
    void testSupportRequestInlineConstruction() {
        Instant now = Instant.now();
        SupportRequest request = new SupportRequest(
                "test-id",
                "Fix login bug",
                "Login fails on mobile devices",
                "dev@example.com",
                SupportPriority.CRITICAL,
                "acct-123",
                SupportStatus.IN_PROGRESS,
                SupportType.BUG_FIX,
                now,
                now
        );

        assertEquals("test-id", request.getId());
        assertEquals("Fix login bug", request.getTitle());
        assertEquals(SupportStatus.IN_PROGRESS, request.getStatus());
        assertEquals(SupportType.BUG_FIX, request.getType());
        assertEquals(SupportPriority.CRITICAL, request.getPriority());
        assertEquals("acct-123", request.getAccountID());
    }

    @Test
    void testSupportRequestNullOptionalFields() {
        Instant now = Instant.now();
        SupportRequest request = new SupportRequest(
                null,
                "Title",
                "Description",
                "email@test.com",
                SupportPriority.LOW,
                null,
                null,
                SupportType.MISC_SUPPORT,
                null,
                null
        );

        assertNull(request.getId());
        assertNull(request.getAccountID());
        assertNull(request.getStatus());
        assertNull(request.getCreatedOnDate());
        assertNull(request.getLastStatusChangeDate());
    }

    @Test
    void testSupportStatusEnum() {
        assertEquals(SupportStatus.NEW, SupportStatus.fromString("new"));
        assertEquals(SupportStatus.IN_PROGRESS, SupportStatus.fromString("in_progress"));
        assertEquals(SupportStatus.BLOCKED, SupportStatus.fromString("blocked"));
        assertEquals(SupportStatus.COMPLETED, SupportStatus.fromString("completed"));
        assertEquals(SupportStatus.DELETED, SupportStatus.fromString("deleted"));
        assertEquals(SupportStatus.NEW, SupportStatus.fromString("NEW"));
    }

    @Test
    void testSupportStatusDescription() {
        assertEquals("new", SupportStatus.NEW.getDescription());
        assertEquals("in_progress", SupportStatus.IN_PROGRESS.getDescription());
        assertEquals("blocked", SupportStatus.BLOCKED.getDescription());
        assertEquals("completed", SupportStatus.COMPLETED.getDescription());
        assertEquals("deleted", SupportStatus.DELETED.getDescription());
    }

    @Test
    void testSupportStatusInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> SupportStatus.fromString("invalid"));
    }

    @Test
    void testSupportTypeEnum() {
        assertEquals(SupportType.NEW_FEATURE, SupportType.fromString("new_feature"));
        assertEquals(SupportType.BUG_FIX, SupportType.fromString("bug_fix"));
        assertEquals(SupportType.MISC_SUPPORT, SupportType.fromString("misc_support"));
        assertEquals(SupportType.BUG_FIX, SupportType.fromString("BUG_FIX"));
    }

    @Test
    void testSupportTypeDescription() {
        assertEquals("new_feature", SupportType.NEW_FEATURE.getDescription());
        assertEquals("bug_fix", SupportType.BUG_FIX.getDescription());
        assertEquals("misc_support", SupportType.MISC_SUPPORT.getDescription());
    }

    @Test
    void testSupportTypeInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> SupportType.fromString("invalid"));
    }

    @Test
    void testSupportPriorityEnum() {
        assertEquals(SupportPriority.LOW, SupportPriority.fromString("low"));
        assertEquals(SupportPriority.MEDIUM, SupportPriority.fromString("medium"));
        assertEquals(SupportPriority.HIGH, SupportPriority.fromString("high"));
        assertEquals(SupportPriority.CRITICAL, SupportPriority.fromString("critical"));
        assertEquals(SupportPriority.HIGH, SupportPriority.fromString("HIGH"));
    }

    @Test
    void testSupportPriorityDescription() {
        assertEquals("low", SupportPriority.LOW.getDescription());
        assertEquals("medium", SupportPriority.MEDIUM.getDescription());
        assertEquals("high", SupportPriority.HIGH.getDescription());
        assertEquals("critical", SupportPriority.CRITICAL.getDescription());
    }

    @Test
    void testSupportPriorityInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> SupportPriority.fromString("invalid"));
    }

    @Test
    void testCreateSupportRequestValidation() {
        assertThrows(NullPointerException.class, () ->
                new CreateSupportRequest(null, "desc", "e@e.com", SupportPriority.HIGH, null, SupportType.BUG_FIX));
        assertThrows(NullPointerException.class, () ->
                new CreateSupportRequest("title", null, "e@e.com", SupportPriority.HIGH, null, SupportType.BUG_FIX));
        assertThrows(NullPointerException.class, () ->
                new CreateSupportRequest("title", "desc", null, SupportPriority.HIGH, null, SupportType.BUG_FIX));
        assertThrows(NullPointerException.class, () ->
                new CreateSupportRequest("title", "desc", "e@e.com", null, null, SupportType.BUG_FIX));
        assertThrows(NullPointerException.class, () ->
                new CreateSupportRequest("title", "desc", "e@e.com", SupportPriority.HIGH, null, null));
    }

    @Test
    void testCreateSupportRequestWithNullAccountID() {
        CreateSupportRequest request = new CreateSupportRequest(
                "title", "desc", "e@e.com", SupportPriority.MEDIUM, null, SupportType.NEW_FEATURE);

        assertNull(request.accountID());
        assertEquals("title", request.title());
    }

    @Test
    void testSupportRequestEqualsAndHashCode() {
        Instant now = Instant.now();
        SupportRequest a = new SupportRequest(
                "id-1", "Title", "Desc", "e@e.com",
                SupportPriority.HIGH, "acct-1", SupportStatus.NEW, SupportType.BUG_FIX, now, now);
        SupportRequest b = new SupportRequest(
                "id-1", "Title", "Desc", "e@e.com",
                SupportPriority.HIGH, "acct-1", SupportStatus.NEW, SupportType.BUG_FIX, now, now);
        SupportRequest c = new SupportRequest(
                "id-2", "Other", "X", "x@x.com",
                SupportPriority.LOW, "acct-2", SupportStatus.COMPLETED, SupportType.MISC_SUPPORT, null, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        assertNotEquals(a, "string");
        assertEquals(a, a);
    }

    @Test
    void testSupportRequestEqualsWithNullFields() {
        SupportRequest a = new SupportRequest(null, null, null, null, null, null, null, null, null, null);
        SupportRequest b = new SupportRequest(null, null, null, null, null, null, null, null, null, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testSupportRequestToString() {
        Instant now = Instant.now();
        SupportRequest request = new SupportRequest(
                "id-1", "Title", null, null,
                null, null, null, null, now, null);

        String string = request.toString();
        assertTrue(string.contains("id-1"));
        assertTrue(string.contains("Title"));
    }
}
