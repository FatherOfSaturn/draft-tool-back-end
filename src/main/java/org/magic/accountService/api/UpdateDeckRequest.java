package org.magic.accountService.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for updating an existing deck's name, description, and card list.
 */
public record UpdateDeckRequest(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("cardIds") List<String> cardIds
) {
}
