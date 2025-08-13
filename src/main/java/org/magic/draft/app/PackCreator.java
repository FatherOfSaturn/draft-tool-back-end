package org.magic.draft.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.Player;
import org.magic.draft.api.PlayerCreationInfo;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardPack;
import org.magic.draft.api.card.Cube;

import jakarta.inject.Inject;

public class PackCreator {
    private static final Logger LOGGER = LogManager.getLogger(CubeDownloader.class);
    
    private final Cube cube;

    @Inject
    public PackCreator(final Cube cube) {
        this.cube = Objects.requireNonNull(cube, "cube Required for Pack Creator");
    }

    public List<Player> createPyramidPacks(final PlayerCreationInfo player1,
                                            final PlayerCreationInfo player2,
                                            final int numberOfDoubleDraftPicksPerPlayer) {
        int cubeSize = cube.getCards().getMainboard().size();

        int packsOf3;
        int packsOf7;
        int packsOf9;
        int packsOf11;

        if (cubeSize <= 540 && cubeSize > 490) {
            LOGGER.info("Large Cube size detected: {}", cube.getName());
            //large cube
            packsOf3 = 8;
            packsOf7 = 8;
            packsOf9 = 8;
            packsOf11 = 8;
        }
        else if (cubeSize <= 490 && cubeSize > 410) {
            LOGGER.info("Medium Cube size detected: {}", cube.getName());
            // medium
            packsOf3 = 6;
            packsOf7 = 4;
            packsOf9 = 8;
            packsOf11 = 8;
        }
        else if (cubeSize <= 410 && cubeSize > 328) {
            LOGGER.info("Small Cube size detected: {}", cube.getName());
            // small
            packsOf3 = 4;
            packsOf7 = 4;
            packsOf9 = 4;
            packsOf11 = 8;
        }
        else {
            throw new Error("Number of cards in cube");
        }

        cube.getCards().shuffleMainboard();
        
        List<CardPack> player1Packs = this.createPlayerPack(packsOf3, packsOf7, packsOf9, packsOf11);
        List<CardPack> player2Packs = this.createPlayerPack(packsOf3, packsOf7, packsOf9, packsOf11);

        Player fullPlayer1 = new Player(player1.getPlayerName(), player1.getPlayerID(), player1Packs, numberOfDoubleDraftPicksPerPlayer, null, 0, false);
        Player fullPlayer2 = new Player(player2.getPlayerName(), player2.getPlayerID(), player2Packs, numberOfDoubleDraftPicksPerPlayer, null, 0, false);

        return List.of(fullPlayer1, fullPlayer2);
    }


    private List<CardPack> createPacks(final int firstPackNumber, final int lastPackNumber, final int numberOfCardsInPack) {
        
        List<CardPack> packs = new ArrayList<>();

        for (int currentPackNumber = firstPackNumber; currentPackNumber < lastPackNumber; currentPackNumber++) {
            List<Card> cardsInPack = this.cube.getCards().drawCardsFromCube(numberOfCardsInPack);
            packs.add(new CardPack(currentPackNumber, cardsInPack, numberOfCardsInPack, false));
        }

        return packs;
    }

    private List<CardPack> createPlayerPack(int packsOf3, int packsOf7, int packsOf9, int packsOf11) {
        int packNumber = 0;
        List<CardPack> playerPacks = new ArrayList<>();

        playerPacks = this.createPacks(packNumber, packNumber + packsOf3, 3);
        packNumber += packsOf3;
        playerPacks.addAll(this.createPacks(packNumber, packNumber + packsOf7, 7));
        packNumber += packsOf7;
        playerPacks.addAll(this.createPacks(packNumber, packNumber + packsOf9, 9));
        packNumber += packsOf9;
        playerPacks.addAll(this.createPacks(packNumber, packNumber + packsOf11, 11));
        
        return playerPacks;
    }


    /*
     * 
For different size cubes
360 => 328 cards ~ 91%
4 p of 3 = 12
4 p of 7 = 28
4 p of 9 = 36
8 p of 11 = 88
=164

450 => 410 cards ~ 91%
6 p of 3 = 18
4 p of 7 = 28
8 p of 9 = 72
8 p of 11 = 88
=206

540 => 490 Cards ~ 91%
8 p of 3 = 24
8 p of 7 = 56
8 p of 9 = 72
8 p of 11 = 88
=240
     * 
     */
}
