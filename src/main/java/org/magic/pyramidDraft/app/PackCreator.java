package org.magic.pyramidDraft.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.pyramidDraft.api.Player;
import org.magic.pyramidDraft.api.PlayerCreationInfo;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardPack;
import org.magic.pyramidDraft.api.card.Cube;

import jakarta.inject.Inject;

/**
 * Creates pyramid draft packs from a cube. Determines pack distribution
 * based on cube size and allocates cards into packs of 3, 7, 9, and 11
 * for each player.
 */
public class PackCreator {
    private static final Logger LOGGER = LogManager.getLogger(PackCreator.class);
    
    private final Cube cube;

    @Inject
    public PackCreator(final Cube cube) {
        this.cube = Objects.requireNonNull(cube, "cube Required for Pack Creator");
    }

    /**
     * Creates pyramid packs for both players based on the cube size.
     * The cube is shuffled and pack counts are determined by size bands:
     * <ul>
     *   <li>Large (491-540): 8 packs each of 3, 7, 9, and 11 cards</li>
     *   <li>Medium (411-490): 6×3, 4×7, 8×9, 8×11</li>
     *   <li>Small (329-410): 4×3, 4×7, 4×9, 8×11</li>
     * </ul>
     *
     * @param player1              info for the first player
     * @param player2              info for the second player
     * @param numberOfDoubleDraftPicksPerPlayer number of double-pick tokens each player receives
     * @return a list of two fully initialized {@link Player} objects
     * @throws IllegalArgumentException if cube size is outside the supported range
     */
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
            packsOf3 = 8;
            packsOf7 = 8;
            packsOf9 = 8;
            packsOf11 = 8;
        }
        else if (cubeSize <= 490 && cubeSize > 410) {
            LOGGER.info("Medium Cube size detected: {}", cube.getName());
            packsOf3 = 6;
            packsOf7 = 4;
            packsOf9 = 8;
            packsOf11 = 8;
        }
        else if (cubeSize <= 410 && cubeSize > 328) {
            LOGGER.info("Small Cube size detected: {}", cube.getName());
            packsOf3 = 4;
            packsOf7 = 4;
            packsOf9 = 4;
            packsOf11 = 8;
        }
        else {
            throw new IllegalArgumentException("Number of cards in cube is outside supported range");
        }

        cube.getCards().shuffleMainboard();
        
        List<CardPack> player1Packs = this.createPlayerPack(packsOf3, packsOf7, packsOf9, packsOf11);
        List<CardPack> player2Packs = this.createPlayerPack(packsOf3, packsOf7, packsOf9, packsOf11);

        Player fullPlayer1 = new Player(player1.playerName(), player1.accountID(), player1Packs, numberOfDoubleDraftPicksPerPlayer, null, 0, false);
        Player fullPlayer2 = new Player(player2.playerName(), player2.accountID(), player2Packs, numberOfDoubleDraftPicksPerPlayer, null, 0, false);

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

}
