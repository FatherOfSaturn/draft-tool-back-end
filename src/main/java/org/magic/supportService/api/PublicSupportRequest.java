package org.magic.supportService.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Public, PII-free view of a {@link SupportRequest} returned by unauthenticated
 * endpoints. Deliberately omits {@code contactEmail} and {@code accountID} so
 * that support request IDs can never be used to harvest submitters' personal data.
 *
 * @param id                  the request ID
 * @param title               a short summary of the request
 * @param description         a detailed explanation of the issue or feature
 * @param priority            the request priority
 * @param status              the request status
 * @param type                the type of request
 * @param createdOnDate       when the request was created
 * @param lastStatusChangeDate when the status last changed, or {@code null}
 */
@JsonPropertyOrder({ "id", "title", "description", "priority", "status",
                     "type", "createdOnDate", "lastStatusChangeDate" })
public record PublicSupportRequest(
    @JsonProperty("id") String id,
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("priority") SupportPriority priority,
    @JsonProperty("status") SupportStatus status,
    @JsonProperty("type") SupportType type,
    @JsonProperty("createdOnDate") Instant createdOnDate,
    @JsonProperty("lastStatusChangeDate") Instant lastStatusChangeDate
) {

    public static PublicSupportRequest from(final SupportRequest request) {
        return new PublicSupportRequest(
                request.getId(),
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.getStatus(),
                request.getType(),
                request.getCreatedOnDate(),
                request.getLastStatusChangeDate()
        );
    }
}
