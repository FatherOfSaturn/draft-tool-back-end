package org.magic.supportService.api;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new support request.
 * All fields except the anti-spam fields are required.
 *
 * <p>The {@code website} field is a honeypot: real users never see it, so any
 * non-blank value identifies a bot and the submission is silently discarded
 * server-side. {@code formToken} (signed, from {@code GET /support/form-token})
 * and {@code cf-turnstile-response} (Cloudflare Turnstile) are enforced only
 * when {@code support.anti-spam.mode} is {@code token-only} or {@code full}.</p>
 *
 * <p>Strict input validation: this endpoint is deserialized with a dedicated
 * Jackson reader (see {@link SupportRequestReader}) that rejects unknown
 * fields, so fields not declared here (for example a client-supplied
 * {@code accountID}) or Mongo-operator-shaped values cannot slip through.</p>
 *
 * @param title              a short summary of the request
 * @param description        a detailed explanation of the issue or feature
 * @param contactEmail       the submitter's email address
 * @param priority           the request priority
 * @param website            honeypot field; must be empty or absent
 * @param formToken          signed form token from {@code GET /support/form-token}
 * @param turnstileToken     Cloudflare Turnstile verification token
 * @param type               the type of request
 */
public record CreateSupportRequest(
    @JsonProperty("title") @NotBlank @Size(max = 200) String title,
    @JsonProperty("description") @NotBlank @Size(max = 4000) String description,
    @JsonProperty("contactEmail") @NotBlank @Email @Size(max = 254) String contactEmail,
    @JsonProperty("priority") @NotNull SupportPriority priority,
    @JsonProperty("website") @Size(max = 200) String website,
    @JsonProperty("formToken") @Size(max = 512) String formToken,
    @JsonProperty("cf-turnstile-response") @Size(max = 2048) String turnstileToken,
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
