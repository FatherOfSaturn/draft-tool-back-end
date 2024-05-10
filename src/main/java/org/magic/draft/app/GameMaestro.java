package org.magic.draft.app;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.GameInfo;
import org.magic.draft.api.Player;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardPack;
import org.magic.draft.util.Pair;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GameMaestro {
    private static final Logger LOGGER = LogManager.getLogger(CubeDownloader.class);
    
    private final CubeDownloader cubeDownloader;
    private final PackMerger packMerger;
    private Player player1;
    private Player player2;

    // TODO: Should this class be a singleton?
    @Inject
    public GameMaestro(final CubeDownloader cubeDownloader,
                       final PackMerger packMerger) {
        this.cubeDownloader = cubeDownloader;
        this.packMerger = packMerger;
    }

    public Uni<List<Player>> startGame(final GameInfo gameInfo) {

        String player1Name = gameInfo.getPlayerInfo().get(0).getPlayerName();
        String player2Name = gameInfo.getPlayerInfo().get(1).getPlayerName();

        return cubeDownloader.getCubeForCubeID(gameInfo.getCubeID())
                             .map(cube -> new PackCreator(cube))
                             .map(packCreator -> packCreator.createPyramidPacks(player1Name, 
                                                                                player2Name, 
                                                                                gameInfo.getNumberOfDoubleDraftPicksPerPlayer()))
                             .invoke(players -> {
                                LOGGER.info("Successfully generated Players and Packs. Caching Data.");
                                this.player1 = players.get(0);
                                this.player2 = players.get(1);
                             });
    }

    public Uni<Card> draftCard(final String playerID, 
                               final int packNumber, 
                               final String cardID, 
                               final boolean isDoublePick) {

        if (player1.getPlayerName().equals(playerID)) {
            LOGGER.info("Drafting {} Card for {}", cardID, playerID);
            return Uni.createFrom().item(player1.draftCard(cardID, packNumber, isDoublePick))
                      .invoke(card -> LOGGER.info("Drafting {} Card for {}", card.getName(), playerID));
        }
        else if (player2.getPlayerName().equals(playerID)) {
            return Uni.createFrom().item(player2.draftCard(cardID, packNumber, isDoublePick))
                      .invoke(card -> LOGGER.info("Drafting {} Card for {}", card.getName(), playerID));
        }
        return Uni.createFrom().failure(new Throwable("Unable to find player to draft Card."));
    }

    public Uni<List<Player>> getCurrentPlayerInfo (){
        if (player1 != null && player2 != null) {
            return Uni.createFrom().item(List.of(player1, player2));
        }
        else {
            throw new Error("No Player information is stored in memory.");
        }
    }

    public Uni<List<Player>> mergeAndSwapPacks() {

        List<CardPack> mergedPlayer1Packs = packMerger.mergePlayerPacks(player1);
        List<CardPack> mergedPlayer2Packs = packMerger.mergePlayerPacks(player2);

        player1.setCards(mergedPlayer2Packs);
        player2.setCards(mergedPlayer1Packs);
        return getCurrentPlayerInfo();
    }
}