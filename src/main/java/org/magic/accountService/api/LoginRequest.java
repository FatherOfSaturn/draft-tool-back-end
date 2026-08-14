package org.magic.accountService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for the login endpoint. Contains a Google OAuth ID token.
 */
public record LoginRequest(@JsonProperty("idToken") @NotBlank @Size(max = 8192) String idToken) {
}
