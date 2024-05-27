package org.magic.draft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.GameCreationInfo;
import org.magic.draft.api.GameInfo;
import org.magic.draft.api.card.Card;
import org.magic.draft.app.GameCoordination.GameCoordinationWorker;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

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
        // Increase PackNumber by 1 to fix the array indexing so user doesnt have to decrement.
        packNumber--;
        return Uni.createFrom().item(gameWorker.draftCard(playerID, packNumber, cardID, isDoublePick, gameID));
    }

    @GET
    @Path("/fetchGameData/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<GameInfo> getCurrentPlayerInfo(@PathParam("gameID") final String gameID) {
        return gameWorker.getGameInfo(gameID);
    }

    @GET
    @Path("/merge/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<GameInfo> triggerPackMergeAndSwap(@PathParam("gameID") final String gameID) {
        return gameWorker.mergeAndSwapPacks(gameID);
    }
}
