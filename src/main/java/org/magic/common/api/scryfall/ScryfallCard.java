package org.magic.common.api.scryfall;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a Magic: The Gathering card as returned by the Scryfall API.
 * Includes card identity, mana cost, type line, images, legalities, and
 * optional {@link ScryfallCardFace} data for multi-faced cards.
 */
public record ScryfallCard(
    @JsonProperty("id") String id,
    @JsonProperty("oracle_id") String oracleId,
    @JsonProperty("name") String name,
    @JsonProperty("mana_cost") String manaCost,
    @JsonProperty("cmc") double cmc,
    @JsonProperty("type_line") String typeLine,
    @JsonProperty("oracle_text") String oracleText,
    @JsonProperty("colors") List<String> colors,
    @JsonProperty("color_identity") List<String> colorIdentity,
    @JsonProperty("power") String power,
    @JsonProperty("toughness") String toughness,
    @JsonProperty("loyalty") String loyalty,
    @JsonProperty("layout") String layout,
    @JsonProperty("set") String set,
    @JsonProperty("set_name") String setName,
    @JsonProperty("rarity") String rarity,
    @JsonProperty("keywords") List<String> keywords,
    @JsonProperty("booster") Boolean booster,
    @JsonProperty("uri") String uri,
    @JsonProperty("image_uris") ScryfallImageUris imageUris,
    @JsonProperty("legalities") Map<String, String> legalities,
    @JsonProperty("card_faces") List<ScryfallCardFace> cardFaces
) {
}
