package org.magic.supportService.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
    @JsonProperty("title") @NotBlank @Size(max = 200) String title,
    @JsonProperty("description") @NotBlank @Size(max = 4000) String description,
    @JsonProperty("contactEmail") @NotBlank @Email @Size(max = 254) String contactEmail,
    @JsonProperty("priority") @NotNull SupportPriority priority,
    @JsonProperty("accountID") @Size(max = 100) String accountID,
    @JsonProperty("type") @NotNull SupportType type
) {

    public CreateSupportRequest {
        Objects.requireNonNull(title, "title is required for CreateSupportRequest");
        Objects.requireNonNull(description, "description is required for CreateSupportRequest");
        Objects.requireNonNull(contactEmail, "contactEmail is required for CreateSupportRequest");
        Objects.requireNonNull(priority, "priority is required for CreateSupportRequest");
        Objects.requireNonNull(type, "type is required for CreateSupportRequest");
    }
}
