package org.magic.draft.app.GameCoordination;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.GameCreationInfo;
import org.magic.draft.api.GameInfo;
import org.magic.draft.api.GameState;
import org.magic.draft.api.GameStatusMessage;
import org.magic.draft.api.Player;
import org.magic.draft.api.PlayerCreationInfo;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardPack;
import org.magic.draft.app.CubeDownloader;
import org.magic.draft.app.PackCreator;
import org.magic.draft.app.PackMerger;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GameCoordinationWorker {
    private static final Logger LOGGER = LogManager.getLogger(GameCoordinationWorker.class);

    private final DbHandler dbHandler;
    private final PackMerger packMerger;
    private final CubeDownloader cubeDownloader;

    @Inject
    public GameCoordinationWorker(final DbHandler dbHandler,
                                  final PackMerger packMerger,
                                  final CubeDownloader cubeDownloader) {
        this.dbHandler = dbHandler;
        this.packMerger = packMerger;
        this.cubeDownloader = cubeDownloader;
    }
    
    public Uni<GameInfo> startGame(final GameCreationInfo gameCreationInfo) {

        PlayerCreationInfo player1Creation = gameCreationInfo.getPlayerInfo().get(0);
        PlayerCreationInfo player2Creation = gameCreationInfo.getPlayerInfo().get(1);

        return cubeDownloader.getCubeForCubeID(gameCreationInfo.getCubeID())
                             .map(cube -> new PackCreator(cube))
                             .map(packCreator -> packCreator.createPyramidPacks(player1Creation, 
                                                                                player2Creation, 
                                                                                gameCreationInfo.getNumberOfDoubleDraftPicksPerPlayer()))
                            //  .invoke(players -> {
                            //     LOGGER.info("Successfully generated Players and Packs. Caching Data.");
                            //     this.player1 = players.get(0);
                            //     this.player2 = players.get(1);
                            //  })
                             .map(players -> new GameInfo(gameCreationInfo.getGameID(), players, GameState.GAME_STARTED))
                             .invoke(gameInfo -> dbHandler.addGame(gameInfo));
    }

    public Card draftCard(final String playerID, 
                          final int packNumber, 
                          final String cardID, 
                          final boolean isDoublePick,
                          final String gameID) {

        LOGGER.info("Fetching Game with ID: {}", gameID);
        GameInfo gameInfo = dbHandler.findGame(gameID);
        
        LOGGER.info("\nRetrieved Game: {}\n", gameID);
        final Player player = gameInfo.getPlayers()
                                       .stream()
                                       .filter(pl -> pl.getPlayerID().equals(playerID))
                                       .findFirst().get();
        LOGGER.info("\nDrafting {} Card for {}\n", cardID, player.getPlayerName());
        
        Card cardDrafted = player.draftCard(cardID, packNumber, isDoublePick);

        LOGGER.info("\nSuccesfully Allocated Draft for Card: {}\n", cardDrafted.getName());

        dbHandler.updatePlayer(gameInfo, player);
        
        return cardDrafted;
    }

    public Uni<GameInfo> getGameInfo(final String gameID) {
        return Uni.createFrom().item(dbHandler.findGame(gameID));
    }

    public Uni<GameStatusMessage> mergeAndSwapPacks(final String gameID) {

        final GameInfo game = dbHandler.findGame(gameID);

        if (game.getPlayers().size() != 2) {
            throw new Error("Game has more than 2 players. Cannot Merge and Swap Cards");
        }

        final Player player1 = game.getPlayers().get(0);
        final Player player2 = game.getPlayers().get(1);

        // TODO: Add a lock on a Game when it is being checked for merging since it is shared by two instances on the front

        if (game.getGameState() == GameState.GAME_MERGED) {
            LOGGER.info("Game was already Merged: {}", gameID);
            return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_MERGED));
        }

        // Check to see if the merge is ready
        if (player1.isReadyForMerge() && player2.isReadyForMerge() && game.getGameState() != GameState.GAME_MERGED) {
            LOGGER.info("Game Merging: {}", gameID);
            List<CardPack> mergedPlayer1Packs = packMerger.mergePlayerPacks(player1);
            List<CardPack> mergedPlayer2Packs = packMerger.mergePlayerPacks(player2);
            player1.setCardPacks(mergedPlayer2Packs);
            player2.setCardPacks(mergedPlayer1Packs);

            player1.resetAfterMerge();
            player2.resetAfterMerge();

            game.setGameState(GameState.GAME_MERGED);
            game.updatePlayers(List.of(player1, player2));

            LOGGER.info("Game Object Updating to: {}", game.toString());

            dbHandler.updateGame(game);

            LOGGER.info("Game was Merged: {}", gameID);
            return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_MERGED));
        }
        LOGGER.info("\nGame {} is not ready for merge, or has already been merged.\nPlayer1: {}\nPlayer2: {}", gameID, 
                                                                                                                       player1.isReadyForMerge(), 
                                                                                                                       player2.isReadyForMerge());
        return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_STARTED));
    }

    public Uni<GameStatusMessage> endGame(String gameID) {

        // very dirty way to check if game state was already updated to complete
        if (!(dbHandler.findGame(gameID).getGameState() == GameState.GAME_COMPLETE)) {
            final GameState gameState = dbHandler.updateGameState(gameID, GameState.GAME_COMPLETE);
            return Uni.createFrom().item(new GameStatusMessage(gameID, gameState));
        }

        return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_COMPLETE));
    }

    public Uni<Response> deleteGamesWithStatus(final GameState gameState) {
        final int recordsDeleted = dbHandler.clearGamesWithStatus(gameState);

        final String message = String.format("Deleted %d records with game state of %s.", recordsDeleted, gameState);

        return Uni.createFrom().item(Response.ok(message).build());
    }
}