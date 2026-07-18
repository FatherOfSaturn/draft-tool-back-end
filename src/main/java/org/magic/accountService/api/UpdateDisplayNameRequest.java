package org.magic.accountService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for updating a user's display name.
 */
public record UpdateDisplayNameRequest(@JsonProperty("displayName") String displayName) {
}
