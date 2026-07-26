package org.magic.supportService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for updating an existing support request.
 * All fields are optional — only provided fields are updated.
 *
 * @param title        the new title, or {@code null} to leave unchanged
 * @param description  the new description, or {@code null} to leave unchanged
 * @param contactEmail the new contact email, or {@code null} to leave unchanged
 * @param priority     the new priority, or {@code null} to leave unchanged
 * @param type         the new type, or {@code null} to leave unchanged
 */
public record UpdateSupportRequest(
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("contactEmail") String contactEmail,
    @JsonProperty("priority") SupportPriority priority,
    @JsonProperty("type") SupportType type
) {
}
