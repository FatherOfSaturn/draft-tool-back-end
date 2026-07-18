package org.magic.common.api.scryfall;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing one face of a multi-faced Magic card (e.g., double-faced cards).
 * Each face has its own name, mana cost, type line, and image URIs.
 */
public record ScryfallCardFace(
    @JsonProperty("name") String name,
    @JsonProperty("mana_cost") String manaCost,
    @JsonProperty("type_line") String typeLine,
    @JsonProperty("oracle_text") String oracleText,
    @JsonProperty("colors") List<String> colors,
    @JsonProperty("power") String power,
    @JsonProperty("toughness") String toughness,
    @JsonProperty("loyalty") String loyalty,
    @JsonProperty("flavor_text") String flavorText,
    @JsonProperty("image_uris") ScryfallImageUris imageUris
) {
}
