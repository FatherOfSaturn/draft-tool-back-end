package org.magic.accountService.api;

import java.time.LocalDateTime;
import java.util.List;

import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A saved deck belonging to a user account. Contains the deck's name,
 * description, and ordered list of card IDs.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({ "deckID", "accountID", "name", "description", "cardIds", "createdAt", "updatedAt" })
public class Deck {

    @BsonProperty("_id")
    @JsonProperty("deckID")
    private String deckID;

    @BsonProperty("accountID")
    @JsonProperty("accountID")
    private String accountID;

    @BsonProperty("name")
    @JsonProperty("name")
    private String name;

    @BsonProperty("description")
    @JsonProperty("description")
    private String description;

    @BsonProperty("cardIds")
    @JsonProperty("cardIds")
    private List<String> cardIds;

    @BsonProperty("createdAt")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @BsonProperty("updatedAt")
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
}
