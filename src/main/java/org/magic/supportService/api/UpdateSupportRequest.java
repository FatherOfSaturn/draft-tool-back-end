package org.magic.supportService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

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
    @JsonProperty("title") @Size(max = 200) String title,
    @JsonProperty("description") @Size(max = 4000) String description,
    @JsonProperty("contactEmail") @Email @Size(max = 254) String contactEmail,
    @JsonProperty("priority") SupportPriority priority,
    @JsonProperty("type") SupportType type
) {
}
