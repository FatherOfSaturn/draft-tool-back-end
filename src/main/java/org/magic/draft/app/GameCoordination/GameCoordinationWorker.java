package org.magic.draft.app.GameCoordination;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.GameCreationInfo;
import org.magic.draft.api.GameInfo;
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
                             .map(players -> new GameInfo(gameCreationInfo.getGameID(), players))
                             .invoke(gameInfo -> dbHandler.addGame(gameInfo));
    }
    public Card draftCard(final String playerID, 
                          final int packNumber, 
                          final String cardID, 
                          final boolean isDoublePick,
                          final String gameID) {

        LOGGER.info("Fetching Game with ID: {}", gameID);
        GameInfo gameInfo = dbHandler.findGame(gameID);
        
        LOGGER.info("\n\n\n\n\nRetrieved Game: {}\n\n\n\n", gameID);
        final Player player = gameInfo.getPlayers()
                                       .stream()
                                       .filter(pl -> pl.getPlayerID().equals(playerID))
                                       .findFirst().get();
        LOGGER.info("\n\n\n\nDrafting {} Card for {}\n\n\n", cardID, player.getPlayerName());
        
        Card cardDrafted = player.draftCard(cardID, packNumber, isDoublePick);

        LOGGER.info("\n\n\n\nSuccesfully Allocated Draft for Card: {}\n\n\n", cardDrafted.getName());

        dbHandler.updatePlayer(gameInfo, player);
        
        return cardDrafted;
    }

    public Uni<GameInfo> getGameInfo(final String gameID) {
        return Uni.createFrom().item(dbHandler.findGame(gameID));
    }

    public Uni<GameInfo> mergeAndSwapPacks(final String gameID) {

        final GameInfo game = dbHandler.findGame(gameID);

        if (game.getPlayers().size() != 2) {
            throw new Error("Game has more than 2 players. Cannot Merge and Swap Cards");
        }

        final Player player1 = game.getPlayers().get(0);
        final Player player2 = game.getPlayers().get(1);

        List<CardPack> mergedPlayer1Packs = packMerger.mergePlayerPacks(player1);
        List<CardPack> mergedPlayer2Packs = packMerger.mergePlayerPacks(player2);

        player1.setCardPacks(mergedPlayer2Packs);
        player2.setCardPacks(mergedPlayer1Packs);
        
        dbHandler.updatePlayer(game, player1);
        dbHandler.updatePlayer(game, player2);
        
        return this.getGameInfo(gameID);
    }
}