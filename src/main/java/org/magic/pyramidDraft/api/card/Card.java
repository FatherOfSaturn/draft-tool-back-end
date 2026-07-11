package org.magic.pyramidDraft.api.card;

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
 * Represents a Magic: The Gathering card within a pyramid draft game.
 * Contains the card's identity, name, and detailed metadata via {@link CardDetails}.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@JsonPropertyOrder({ "name", "cardID", "details" })
public class Card {
    private static final Logger LOGGER = LogManager.getLogger(Card.class);

    private String name;
    @Setter(AccessLevel.NONE)
    private final String cardID;
    @Setter(AccessLevel.NONE)
    private final int cmc;
    @JsonProperty("details")
    @BsonProperty("details")
    private CardDetails cardDetails;
    @Setter(AccessLevel.NONE)
    private final String type_line;

    @JsonCreator
    @BsonCreator
    public Card(@JsonProperty("name")     @BsonProperty("name")    final String name,
                @JsonProperty("details")  @BsonProperty("details") final CardDetails cardDetails,
                @JsonProperty("cardID")   @BsonProperty("cardID")  final String cardID,
                @JsonProperty("cmc")      @BsonProperty("cmc")     final Integer cmc,
                @JsonProperty("type_line")@BsonProperty("type_line")     final String type_line) {
        this.cardDetails = Objects.requireNonNull(cardDetails, "cardDetails Required for card");
        this.cardID = Objects.requireNonNull(cardID, "cardID required for card");

        if (name == null) {
            LOGGER.debug("Card with ID of {} does not have a name on the Card SuperType", cardID);
            this.name = cardDetails.getName();
        } else {
            this.name = name;
        }
        if (cmc == null) {
            LOGGER.debug("Card with ID of {} does not have a CMC on Card Supertype.", cardID);
            this.cmc = cardDetails.getCmc();
        } else {
            this.cmc = cmc;
        }
        if (type_line == null) {
            LOGGER.debug("Card with ID of {} does not have a type on Card Supertype. Getting SubType: {}", cardID, cardDetails.getType());
            this.type_line = cardDetails.getType();
        } else {
            this.type_line = type_line;
        }
    }
}
