package org.magic.draft;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.GameInfo;
import org.magic.draft.api.Player;
import org.magic.draft.api.card.Card;
import org.magic.draft.app.GameMaestro;
import org.magic.draft.util.Pair;

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

    final GameMaestro gameMaestro;

    public GameResource(final GameMaestro gameMaestro) {
        this.gameMaestro = gameMaestro;
    }
    
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<List<Player>> createAndStartGame(final GameInfo gameinfo) {
        return gameMaestro.startGame(gameinfo);
    }

    @POST
    @Path("{playerName}/draftCard/{packNumber}/{cardID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Card> draftCard(@PathParam("playerName") final String playerName,
                               @PathParam("packNumber") int packNumber,
                               @PathParam("cardID") final String cardID,
                               @QueryParam("doublePick") final boolean isDoublePick) {
        // Increase PackNumber by 1 to fix the array indexing so user doesnt have to decrement.
        packNumber++;
        return gameMaestro.draftCard(playerName, packNumber, cardID, isDoublePick);
    }

    @GET
    @Path("/fetchPlayerData")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<Player>> getCurrentPlayerInfo() {
        return gameMaestro.getCurrentPlayerInfo();
    }

    @GET
    @Path("/merge")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<Player>> triggerPackMergeAndSwap() {
        return gameMaestro.mergeAndSwapPacks();
    }
}
