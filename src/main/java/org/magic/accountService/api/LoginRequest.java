package org.magic.accountService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for the login endpoint. Contains a Google OAuth ID token.
 */
public record LoginRequest(@JsonProperty("idToken") String idToken) {
}
