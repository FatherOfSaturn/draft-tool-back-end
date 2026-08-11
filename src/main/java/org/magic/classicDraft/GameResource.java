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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/classic-game")
public class GameResource {
    private static final Logger LOGGER = LogManager.getLogger(GameResource.class);

    final ClassicGameCoordinationWorker gameWorker;

    public GameResource(final ClassicGameCoordinationWorker gameWorker) {
        this.gameWorker = gameWorker;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<ClassicGameInfo> createAndStartGame(final ClassicGameCreationInfo creationInfo) {
        LOGGER.info("Call to create Classic Game for cube: {}", creationInfo.cubeID());
        return gameWorker.startGame(creationInfo);
    }

    @POST
    @Path("/{gameID}/player/{playerName}/draftCard/{cardID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Card> draftCard(@PathParam("playerName") final String playerName,
                                @PathParam("cardID") final String cardID,
                                @PathParam("gameID") final String gameID) {
        LOGGER.info("Call to draft card: {} for classic game: {}", cardID, gameID);
        return Uni.createFrom().item(gameWorker.draftCard(playerName, cardID, gameID));
    }

    @GET
    @Path("/{gameID}/player/{playerName}/draftCheck")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<PlayerDraftCheck> getDraftCheck(@PathParam("gameID") final String gameID,
                                                @PathParam("playerName") final String playerName) {
        LOGGER.info("Call to check draft status for player: {} in classic game: {}", playerName, gameID);
        return gameWorker.getDraftCheck(gameID, playerName);
    }

    @GET
    @Path("/{gameID}/player/{playerName}/draftData")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<PlayerDraftData> getDraftData(@PathParam("gameID") final String gameID,
                                              @PathParam("playerName") final String playerName) {
        LOGGER.info("Call to fetch draft data for player: {} in classic game: {}", playerName, gameID);
        return gameWorker.getDraftData(gameID, playerName);
    }

    @GET
    @Path("/fetchGameData/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<ClassicGameInfo> getCurrentGameInfo(@PathParam("gameID") final String gameID) {
        LOGGER.info("Call to fetch Classic Game: {}", gameID);
        return gameWorker.getGameInfo(gameID);
    }

    @POST
    @Path("/end/{gameID}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<GameStatusMessage> endGame(@PathParam("gameID") final String gameID) {
        LOGGER.info("Call to end Classic Game: {}", gameID);
        return gameWorker.endGame(gameID);
    }

    @GET
    @Path("/history/{playerName}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<List<ClassicGameSummary>> getGameHistory(@PathParam("playerName") final String playerName) {
        LOGGER.info("Call to fetch classic game history for player: {}", playerName);
        return gameWorker.getGameHistory(playerName);
    }
}
