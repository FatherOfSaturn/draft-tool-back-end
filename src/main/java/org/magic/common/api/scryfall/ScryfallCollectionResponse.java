package org.magic.common.api.scryfall;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body from Scryfall's POST /cards/collection endpoint.
 * Contains cards that matched and a list of identifiers that were not found.
 */
public record ScryfallCollectionResponse(
    @JsonProperty("data") List<ScryfallCard> data,
    @JsonProperty("not_found") List<ScryfallCollectionRequest.ScryfallIdentifier> notFound
) {
}
