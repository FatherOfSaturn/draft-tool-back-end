package org.magic.common.api.scryfall;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a related card entry from Scryfall's {@code all_parts} array,
 * used to identify meld results, combo pieces, and tokens.
 */
public record ScryfallRelatedCard(
    @JsonProperty("id") String id,
    @JsonProperty("component") String component,
    @JsonProperty("name") String name,
    @JsonProperty("type_line") String typeLine,
    @JsonProperty("uri") String uri
) {
}
