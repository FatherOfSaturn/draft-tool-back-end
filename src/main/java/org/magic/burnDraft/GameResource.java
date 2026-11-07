package org.magic.burnDraft;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.burnDraft.api.BurnGameCreationInfo;
import org.magic.burnDraft.api.BurnGameInfo;
import org.magic.burnDraft.api.BurnGameSummary;
import org.magic.burnDraft.api.BurnTurnRequest;
import org.magic.burnDraft.api.PlayerBurnCheck;
import org.magic.burnDraft.api.PlayerBurnData;
import org.magic.burnDraft.app.GameCoordination.BurnGameCoordinationWorker;
import org.magic.common.card.Card;
import org.magic.common.game.GameStatusMessage;

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
 * REST resource for burn drafts: a rotating draft where each turn drafts
 * several cards and burns several others before passing the pack on.
 * Player identity on draft actions is carried by the {@code X-Player-Token}
 * header, never in the URL.
 */
@Path("/burn-game")
@Tag(name = "Burn Draft", description = "Rotating draft games where players draft and burn cards from each pack.")
public class GameResource {
    private static final Logger LOGGER = LogManager.getLogger(GameResource.class);

    final BurnGameCoordinationWorker gameWorker;

    public GameResource(final BurnGameCoordinationWorker gameWorker) {
        this.gameWorker = gameWorker;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create and start a burn game",
            description = "Creates a burn draft game from the given cube and players, dealing the initial packs, "
                    + "and returns the game state.")
    public Uni<BurnGameInfo> createAndStartGame(@Valid final BurnGameCreationInfo creationInfo) {
        LOGGER.info("Call to create Burn Game for cube: {}", creationInfo.cubeID());
        return gameWorker.startGame(creationInfo);
    }

    /**
     * Takes one turn: drafts the submitted cards and burns the submitted cards
     * from the pack the player currently holds, then passes the remainder on.
     *
     * @param gameID      the game ID
     * @param playerToken the player's draft token, sent via the {@code X-Player-Token} header
     * @param request     the card IDs to draft and to burn
     * @return the cards drafted this turn
     */
    @POST
    @Path("/{gameID}/draft")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Take a burn-draft turn",
            description = "Drafts and burns the submitted cards from the player's current pack, then passes the "
                    + "pack on. The player is identified by the X-Player-Token header.")
    public Uni<List<Card>> takeTurn(@PathParam("gameID") @Size(max = 64) final String gameID,
                                    @HeaderParam("X-Player-Token") @Size(max = 64) final String playerToken,
                                    @Valid final BurnTurnRequest request) {
        LOGGER.info("Call to take a burn turn for game: {}", gameID);
        return gameWorker.takeTurn(playerToken, gameID, request);
    }

    @GET
    @Path("/{gameID}/draftCheck")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Check draft status",
            description = "Returns whether the identified player can currently take a turn in the given game.")
    public Uni<PlayerBurnCheck> getDraftCheck(@PathParam("gameID") @Size(max = 64) final String gameID,
                                              @HeaderParam("X-Player-Token") @Size(max = 64) final String playerToken) {
        LOGGER.info("Call to check draft status for burn game: {}", gameID);
        return gameWorker.getDraftCheck(gameID, playerToken);
    }

    @GET
    @Path("/{gameID}/draftData")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get draft data",
            description = "Returns the identified player's burn draft state, including the game's turn configuration.")
    public Uni<PlayerBurnData> getDraftData(@PathParam("gameID") @Size(max = 64) final String gameID,
                                            @HeaderParam("X-Player-Token") @Size(max = 64) final String playerToken) {
        LOGGER.info("Call to fetch draft data for burn game: {}", gameID);
        return gameWorker.getDraftData(gameID, playerToken);
    }

    @GET
    @Path("/fetchGameData/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get game info",
            description = "Returns the current state of a burn draft game.")
    public Uni<BurnGameInfo> getCurrentGameInfo(@PathParam("gameID") @Size(max = 64) final String gameID) {
        LOGGER.info("Call to fetch Burn Game: {}", gameID);
        return gameWorker.getGameInfo(gameID);
    }

    @POST
    @Path("/end/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "End a game",
            description = "Marks the given burn draft game complete and returns the resulting status message.")
    public Uni<GameStatusMessage> endGame(@PathParam("gameID") @Size(max = 64) final String gameID) {
        LOGGER.info("Call to end Burn Game: {}", gameID);
        return gameWorker.endGame(gameID);
    }

    @GET
    @Path("/history/{playerName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get burn game history",
            description = "Returns the burn draft game history for the given player name.")
    public Uni<List<BurnGameSummary>> getGameHistory(@PathParam("playerName") @Size(max = 50) final String playerName) {
        LOGGER.info("Call to fetch burn game history for player: {}", playerName);
        return gameWorker.getGameHistory(playerName);
    }
}
