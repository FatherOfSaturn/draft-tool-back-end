package org.magic.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal error payload returned for client-facing failures such as rate limiting.
 */
public record ErrorMessage(@JsonProperty("message") String message) {
}
