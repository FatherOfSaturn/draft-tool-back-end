package org.magic.pyramidDraft.api.card;

import java.util.List;
import java.util.Objects;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Detailed metadata for a Magic card, including set info, images, type line,
 * mana cost, and power/toughness. Populated from CubeCobra's cube data format.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@JsonPropertyOrder({ "name", "set", "set_name", "cmc", "type", "scryfall_id", "image_small", "image_normal", "image_flip" })
public class CardDetails {
    private static final Logger LOGGER = LogManager.getLogger(CardDetails.class);

    private String set;
    private String set_name;
    private String scryfall_id;
    private String image_small;
    private String image_normal;
    @JsonProperty("image_flip")
    @BsonProperty("image_flip")
    @Setter(AccessLevel.NONE)
    private final String imageFlip;
    @Setter(AccessLevel.NONE)
    private final String name;
    private String type;
    @Setter(AccessLevel.NONE)
    private final Integer cmc;
    private List<String> parsed_cost;

    @JsonCreator
    @BsonCreator
    public CardDetails(@JsonProperty("set")          @BsonProperty("set")          final String set,
                       @JsonProperty("set_name")     @BsonProperty("set_name")     final String setName,
                       @JsonProperty("scryfall_id")  @BsonProperty("scryfall_id")  final String scryfallId,
                       @JsonProperty("image_small")  @BsonProperty("image_small")  final String imageSmall,
                       @JsonProperty("image_normal") @BsonProperty("image_normal") final String imageNormal,
                       @JsonProperty("image_flip")   @BsonProperty("image_flip")   final String imageFlip,
                       @JsonProperty("name")         @BsonProperty("name")         final String name,
                       @JsonProperty("type")         @BsonProperty("type")         final String type,
                       @JsonProperty("cmc")          @BsonProperty("cmc")          final Integer cmc,
                       @JsonProperty("parsed_cost")  @BsonProperty("parsed_cost")  final List<String> parsed_cost) {
        this.set = Objects.requireNonNull(set, "set Required for card details");
        this.set_name = Objects.requireNonNull(setName, "set_name Required for card details");
        this.scryfall_id = Objects.requireNonNull(scryfallId, "scryfallId Required for card details");
        this.image_small = Objects.requireNonNull(imageSmall, "imageSmall Required for card details");
        this.image_normal = Objects.requireNonNull(imageNormal, "imageNormal Required for card details");
        this.name = Objects.requireNonNull(name, "name Required for card details");
        this.parsed_cost = Objects.requireNonNullElse(parsed_cost, List.of());
        this.imageFlip = imageFlip;

        if (cmc == null) {
            LOGGER.warn("Card {} has null cmc", scryfallId);
        }
        if (type == null) {
            LOGGER.warn("Card {} has null type", scryfallId);
        }
        this.cmc = Objects.requireNonNull(cmc, "cmc Required for card details");
        this.type = Objects.requireNonNull(type, "type Required for card details");
    }
}
