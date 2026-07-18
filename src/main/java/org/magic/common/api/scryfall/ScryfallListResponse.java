package org.magic.common.api.scryfall;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a paginated list response from the Scryfall API.
 * Contains a list of cards, pagination flags, and optional warnings.
 */
public record ScryfallListResponse(
    @JsonProperty("object") String object,
    @JsonProperty("data") List<ScryfallCard> data,
    @JsonProperty("has_more") Boolean hasMore,
    @JsonProperty("next_page") String nextPage,
    @JsonProperty("total_cards") Integer totalCards,
    @JsonProperty("warnings") List<String> warnings
) {

    /**
     * Returns an empty list response, used as a fallback when a Scryfall API call fails.
     */
    public static ScryfallListResponse empty() {
        return new ScryfallListResponse("list", List.of(), false, null, 0, null);
    }
}
