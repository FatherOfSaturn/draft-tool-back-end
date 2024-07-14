package org.magic.draft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.GameCreationInfo;
import org.magic.draft.api.GameInfo;
import org.magic.draft.api.GameState;
import org.magic.draft.api.GameStatusMessage;
import org.magic.draft.api.card.Card;
import org.magic.draft.app.GameCoordination.GameCoordinationWorker;
import org.magic.draft.util.JsonUtility;

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

@Path("/game")
public class GameResource {
    private static final Logger LOGGER = LogManager.getLogger(GameResource.class);

    final GameCoordinationWorker gameWorker;

    public GameResource(final GameCoordinationWorker gameWorker) {
        this.gameWorker = gameWorker;
    }
    
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<GameInfo> createAndStartGame(final GameCreationInfo gameinfo) {
        LOGGER.info("Call to create Game for cube: {}", gameinfo.getCubeID());
        return gameWorker.startGame(gameinfo);
    }

    @POST
    @Path("/{gameID}/{playerID}/draftCard/{packNumber}/{cardID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Card> draftCard(@PathParam("playerID") final String playerID,
                               @PathParam("packNumber") int packNumber,
                               @PathParam("cardID") final String cardID,
                               @PathParam("gameID") final String gameID,
                               @QueryParam("doublePick") final boolean isDoublePick) {
        LOGGER.info("Call to draft card: {}\n For game: {}\n", cardID, gameID);
        return Uni.createFrom().item(gameWorker.draftCard(playerID, packNumber, cardID, isDoublePick, gameID));
    }

    @GET
    @Path("/fetchGameData/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<GameInfo> getCurrentPlayerInfo(@PathParam("gameID") final String gameID) {
        LOGGER.info("Call to fetch Game: {}", gameID);
        return gameWorker.getGameInfo(gameID);
    }

    // Call will return null if game is not ready for merge
    // Otherwise, return the GameID
    @GET
    @Path("/merge/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<GameStatusMessage> triggerPackMergeAndSwap(@PathParam("gameID") final String gameID) {
        LOGGER.info("Call to merge Game: {}", gameID);
        return gameWorker.mergeAndSwapPacks(gameID)
                         .invoke(status -> LOGGER.info("Replying with Game Status Message: {}", JsonUtility.getInstance().toJson(status)));
    }

    // Call will return null if game is not ready for merge
    // Otherwise, return the GameID
    @GET
    @Path("/end/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<GameStatusMessage> endGame(@PathParam("gameID") final String gameID) {
        LOGGER.info("Call to End Game: {}", gameID);
        return gameWorker.endGame(gameID)
                         .invoke(status -> LOGGER.info("Replying with Game Status Message: {}", JsonUtility.getInstance().toJson(status)));
    }

    @DELETE
    @Path("/end/admin/delete/random/{gameState}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> deleteGamesWithStatus(@PathParam("gameState") final String gameState) {
        LOGGER.info("Call to delete Games with state of {}", gameState);
        
        return gameWorker.deleteGamesWithStatus(GameState.fromString(gameState));
    }
}
