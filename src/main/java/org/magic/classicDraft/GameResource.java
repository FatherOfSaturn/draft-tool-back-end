package org.magic.classicDraft;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.classicDraft.api.ClassicGameCreationInfo;
import org.magic.classicDraft.api.ClassicGameInfo;
import org.magic.classicDraft.api.ClassicGameSummary;
import org.magic.classicDraft.api.PlayerDraftCheck;
import org.magic.classicDraft.api.PlayerDraftData;
import org.magic.classicDraft.app.GameCoordination.ClassicGameCoordinationWorker;
import org.magic.pyramidDraft.api.GameStatusMessage;
import org.magic.pyramidDraft.api.card.Card;

import io.smallrye.mutiny.Uni;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST resource for classic (8-player rotisserie-style) drafts.
 * Player identity on draft actions is carried by the {@code X-Player-Token}
 * header, never in the URL.
 */
@Path("/classic-game")
@Tag(name = "Classic Draft", description = "Classic rotisserie-style draft games.")
public class GameResource {
    private static final Logger LOGGER = LogManager.getLogger(GameResource.class);

    final ClassicGameCoordinationWorker gameWorker;

    public GameResource(final ClassicGameCoordinationWorker gameWorker) {
        this.gameWorker = gameWorker;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create and start a classic game",
            description = "Creates a classic draft game from the given cube and players, dealing the initial packs, " +
                    "and returns the game state.")
    public Uni<ClassicGameInfo> createAndStartGame(@Valid final ClassicGameCreationInfo creationInfo) {
        LOGGER.info("Call to create Classic Game for cube: {}", creationInfo.cubeID());
        return gameWorker.startGame(creationInfo);
    }

    /**
     * Drafts a card from a player's pack. The player is authenticated by their
     * {@code X-Player-Token} header — never by a URL parameter, which would leak
     * the bearer token into access logs.
     *
     * @param gameID      the game ID
     * @param cardID      the card ID to draft
     * @param playerToken the player's draft token, sent via the {@code X-Player-Token} header
     * @return the drafted card
     */
    @POST
    @Path("/{gameID}/draftCard/{cardID}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Draft a card",
            description = "Drafts the given card from the player's current pack. " +
                    "The player is identified by the X-Player-Token header.")
    public Uni<Card> draftCard(@PathParam("gameID") @Size(max = 64) final String gameID,
                               @PathParam("cardID") @Size(max = 64) final String cardID,
                               @HeaderParam("X-Player-Token") @Size(max = 64) final String playerToken) {
        LOGGER.info("Call to draft card: {} for classic game: {}", cardID, gameID);
        return Uni.createFrom().item(gameWorker.draftCard(playerToken, cardID, gameID));
    }

    @GET
    @Path("/{gameID}/draftCheck")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Check draft status",
            description = "Returns whether the identified player can currently draft in the given game.")
    public Uni<PlayerDraftCheck> getDraftCheck(@PathParam("gameID") @Size(max = 64) final String gameID,
                                               @HeaderParam("X-Player-Token") @Size(max = 64) final String playerToken) {
        LOGGER.info("Call to check draft status for classic game: {}", gameID);
        return gameWorker.getDraftCheck(gameID, playerToken);
    }

    @GET
    @Path("/{gameID}/draftData")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get draft data",
            description = "Returns the identified player's full draft state (packs, cards drafted, direction, state).")
    public Uni<PlayerDraftData> getDraftData(@PathParam("gameID") @Size(max = 64) final String gameID,
                                             @HeaderParam("X-Player-Token") @Size(max = 64) final String playerToken) {
        LOGGER.info("Call to fetch draft data for classic game: {}", gameID);
        return gameWorker.getDraftData(gameID, playerToken);
    }

    @GET
    @Path("/fetchGameData/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get game info",
            description = "Returns the current state of a classic draft game.")
    public Uni<ClassicGameInfo> getCurrentGameInfo(@PathParam("gameID") @Size(max = 64) final String gameID) {
        LOGGER.info("Call to fetch Classic Game: {}", gameID);
        return gameWorker.getGameInfo(gameID);
    }

    @POST
    @Path("/end/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "End a game",
            description = "Marks the given classic draft game complete and returns the resulting status message.")
    public Uni<GameStatusMessage> endGame(@PathParam("gameID") @Size(max = 64) final String gameID) {
        LOGGER.info("Call to end Classic Game: {}", gameID);
        return gameWorker.endGame(gameID);
    }

    @GET
    @Path("/history/{playerName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get classic game history",
            description = "Returns the classic draft game history for the given player name.")
    public Uni<List<ClassicGameSummary>> getGameHistory(@PathParam("playerName") @Size(max = 50) final String playerName) {
        LOGGER.info("Call to fetch classic game history for player: {}", playerName);
        return gameWorker.getGameHistory(playerName);
    }
}
