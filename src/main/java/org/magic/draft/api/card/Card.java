package org.magic.draft.api.card;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "name", "cardID", "details" })
public class Card {
    private static final Logger LOGGER = LogManager.getLogger(Card.class);

    private String name;
    private final String cardID;
    private CardDetails cardDetails;

    @JsonCreator
    @BsonCreator
    public Card(@JsonProperty("name") @BsonProperty("name") final String name,
                @JsonProperty("details") @BsonProperty("details") final CardDetails cardDetails,
                @JsonProperty("cardID") @BsonProperty("cardID") final String cardID) {
        if (name == null) {
            LOGGER.warn("Card with ID of {} does not have a name on the Card SuperType", cardID);
            this.name = cardDetails.getName();
        }
        else {
            this.name = name;
        }
        this.cardDetails = Objects.requireNonNull(cardDetails, "cardDetails Required for card");
        this.cardID = Objects.requireNonNull(cardID, "cardID required for card");
    }

    public String getCardID() {
        return cardID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CardDetails getDetails() {
        return cardDetails;
    }

    public void setDetails(CardDetails cardDetails) {
        this.cardDetails = cardDetails;
    }
}