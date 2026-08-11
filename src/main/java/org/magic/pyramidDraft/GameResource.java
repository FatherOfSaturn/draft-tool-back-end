package org.magic.pyramidDraft;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.common.util.JsonUtility;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.GameStatusMessage;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.app.GameCoordination.GameCoordinationWorker;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for managing pyramid draft games.
 * Provides endpoints for creating games, drafting cards, merging packs,
 * and ending games.
 */
@Path("/game")
public class GameResource {
    private static final Logger LOGGER = LogManager.getLogger(GameResource.class);

    final GameCoordinationWorker gameWorker;

    public GameResource(final GameCoordinationWorker gameWorker) {
        this.gameWorker = gameWorker;
    }
    
    /**
     * Creates a new pyramid draft game. Downloads the cube, generates packs for both players,
     * persists the game state, and returns the initial {@link GameInfo}.
     *
     * @param gameinfo the game creation parameters (cube ID, player info, double-pick count)
     * @return the created game
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<GameInfo> createAndStartGame(final GameCreationInfo gameinfo) {
        LOGGER.info("Call to create Game for cube: {}", gameinfo.cubeID());
        return gameWorker.startGame(gameinfo);
    }

    /**
     * Drafts a card from a player's pack. If the {@code doublePick} query parameter is true,
     * a double-pick token is consumed and the player may draft from the same pack again.
     *
     * @param accountID  the account drafting
     * @param packNumber the pack to draft from
     * @param cardID    the Scryfall card ID to draft
     * @param gameID    the game ID
     * @param isDoublePick whether to use a double-pick token
     * @return the drafted card
     */
    @POST
    @Path("/{gameID}/{accountID}/draftCard/{packNumber}/{cardID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Card> draftCard(@PathParam("accountID") final String accountID,
                               @PathParam("packNumber") int packNumber,
                               @PathParam("cardID") final String cardID,
                               @PathParam("gameID") final String gameID,
                               @QueryParam("doublePick") final boolean isDoublePick) {
        LOGGER.info("Call to draft card: {}\n For game: {}\n", cardID, gameID);
        return Uni.createFrom().item(gameWorker.draftCard(accountID, packNumber, cardID, isDoublePick, gameID));
    }

    /**
     * Fetches the current state of a game, including both players' packs and drafted cards.
     *
     * @param gameID the game to fetch
     * @return the current game state
     */
    @GET
    @Path("/fetchGameData/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<GameInfo> getCurrentPlayerInfo(@PathParam("gameID") final String gameID) {
        LOGGER.info("Call to fetch Game: {}", gameID);
        return gameWorker.getGameInfo(gameID);
    }

    /**
     * Triggers the pack merge and swap phase. Both players must have completed all their
     * draft rounds before this succeeds. Merges same-size packs and swaps them between
     * players for the next draft phase.
     *
     * @param gameID the game to merge
     * @return a status message indicating whether the merge completed or the game is still in progress
     */
    @GET
    @Path("/merge/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<GameStatusMessage> triggerPackMergeAndSwap(@PathParam("gameID") final String gameID) {
        LOGGER.info("Call to merge Game: {}", gameID);
        return gameWorker.mergeAndSwapPacks(gameID)
                         .invoke(status -> LOGGER.info("Replying with Game Status Message: {}", JsonUtility.getInstance().toJson(status)));
    }

    /**
     * Ends a game by transitioning its state to GAME_COMPLETE. Idempotent — if the game
     * is already complete, the same status is returned.
     *
     * @param gameID the game to end
     * @return a status message with GAME_COMPLETE state
     */
    @GET
    @Path("/end/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<GameStatusMessage> endGame(@PathParam("gameID") final String gameID) {
        LOGGER.info("Call to End Game: {}", gameID);
        return gameWorker.endGame(gameID)
                         .invoke(status -> LOGGER.info("Replying with Game Status Message: {}", JsonUtility.getInstance().toJson(status)));
    }

    /**
     * Deletes a single game by its ID. Administrative cleanup utility.
     * Players, packs, and drafted cards embedded in the game are removed with it.
     * Idempotent — if the game is already gone, a 404 is returned.
     *
     * @param gameID the game to delete
     * @return 204 No Content if deleted, 404 if not found
     */
    @DELETE
    @Path("/end/admin/delete/{gameID}")
    public Uni<Response> deleteGame(@PathParam("gameID") final String gameID) {
        LOGGER.info("Call to delete Game: {}", gameID);
        return gameWorker.deleteGame(gameID);
    }
}
