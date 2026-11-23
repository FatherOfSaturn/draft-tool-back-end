package org.magic.burnDraft.api;

import org.magic.common.game.DraftDirection;
import org.magic.common.game.GameState;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Full per-player burn draft state, including the game's turn configuration so
 * clients can build a valid turn request (draft list + burn list) without
 * duplicating the rules.
 */
@JsonPropertyOrder({ "gameID", "gameState", "draftDirection", "cardsDraftedPerPack",
                     "cardsBurnedPerPack", "remainderPolicy", "player" })
public record PlayerBurnData(
    @JsonProperty("gameID") String gameID,
    @JsonProperty("gameState") GameState gameState,
    @JsonProperty("draftDirection") DraftDirection draftDirection,
    @JsonProperty("cardsDraftedPerPack") int cardsDraftedPerPack,
    @JsonProperty("cardsBurnedPerPack") int cardsBurnedPerPack,
    @JsonProperty("remainderPolicy") BurnRemainderPolicy remainderPolicy,
    @JsonProperty("player") BurnPlayerSnapshot player
) {
}
