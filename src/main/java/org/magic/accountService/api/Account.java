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
 * User account entity. Stores Google OAuth identity info, display name,
 * and references to the user's saved decks.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({ "accountID", "email", "displayName", "googleSub", "deckIDs", "createdAt" })
public class Account {

    @BsonProperty("_id")
    @JsonProperty("accountID")
    private String accountID;

    @BsonProperty("email")
    @JsonProperty("email")
    private String email;

    @BsonProperty("displayName")
    @JsonProperty("displayName")
    private String displayName;

    @BsonProperty("googleSub")
    @JsonProperty("googleSub")
    private String googleSub;

    @JsonProperty("deckIDs")
    private List<String> deckIDs;

    @BsonProperty("createdAt")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
}
