package org.magic.common.api.scryfall;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for Scryfall's POST /cards/collection endpoint,
 * which accepts up to 75 card identifiers in a single call.
 */
public record ScryfallCollectionRequest(
    @JsonProperty("identifiers") List<ScryfallIdentifier> identifiers
) {
    public record ScryfallIdentifier(@JsonProperty("name") String name) {}
}
