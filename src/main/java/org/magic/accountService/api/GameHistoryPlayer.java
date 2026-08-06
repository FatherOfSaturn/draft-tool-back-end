package org.magic.accountService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-player progress entry within a unified game history entry. Fields are
 * shared across game types; format-specific fields (cardsLeftToDraft,
 * draftOrderNumber) are null when not applicable to the game type.
 */
public record GameHistoryPlayer(
    @JsonProperty("name") String name,
    @JsonProperty("displayName") String displayName,
    @JsonProperty("accountID") String accountID,
    @JsonProperty("currentPack") Integer currentPack,
    @JsonProperty("totalPacks") Integer totalPacks,
    @JsonProperty("doneDrafting") Boolean doneDrafting,
    @JsonProperty("cardsLeftToDraft") Integer cardsLeftToDraft,
    @JsonProperty("draftOrderNumber") Integer draftOrderNumber
) {
}
