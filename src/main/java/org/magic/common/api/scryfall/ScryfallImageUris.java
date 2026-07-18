package org.magic.common.api.scryfall;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO containing image URIs for a card in various sizes and crops
 * as provided by the Scryfall API.
 */
public record ScryfallImageUris(
    @JsonProperty("small") String small,
    @JsonProperty("normal") String normal,
    @JsonProperty("large") String large,
    @JsonProperty("png") String png,
    @JsonProperty("art_crop") String artCrop,
    @JsonProperty("border_crop") String borderCrop
) {
}
