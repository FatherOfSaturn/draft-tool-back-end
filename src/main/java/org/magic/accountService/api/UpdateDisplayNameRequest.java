package org.magic.accountService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating a user's display name.
 * Bounded in length and free of control characters (newlines, etc.) to prevent
 * oversized or log-injecting display names.
 */
public record UpdateDisplayNameRequest(
    @JsonProperty("displayName")
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "displayName must not contain control characters")
    String displayName
) {
}
