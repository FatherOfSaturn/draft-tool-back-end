package org.magic.accountService.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for creating a new deck. Contains the deck name,
 * description, and initial list of card IDs.
 */
public record CreateDeckRequest(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("cardIds") List<String> cardIds
) {
}
