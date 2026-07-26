package org.magic.supportService.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for creating a new support request.
 * All fields except {@code accountID} are required.
 *
 * @param title       a short summary of the request
 * @param description a detailed explanation of the issue or feature
 * @param contactEmail the submitter's email address
 * @param priority    the request priority
 * @param accountID   the associated account ID, or {@code null} for anonymous submissions
 * @param type        the type of request
 */
public record CreateSupportRequest(
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("contactEmail") String contactEmail,
    @JsonProperty("priority") SupportPriority priority,
    @JsonProperty("accountID") String accountID,
    @JsonProperty("type") SupportType type
) {

    public CreateSupportRequest {
        Objects.requireNonNull(title, "title is required for CreateSupportRequest");
        Objects.requireNonNull(description, "description is required for CreateSupportRequest");
        Objects.requireNonNull(contactEmail, "contactEmail is required for CreateSupportRequest");
        Objects.requireNonNull(priority, "priority is required for CreateSupportRequest");
        Objects.requireNonNull(type, "type is required for CreateSupportRequest");
    }
}
