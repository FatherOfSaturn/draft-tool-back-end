package org.magic.draft.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.Player;
import org.magic.draft.api.PlayerCreationInfo;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardPack;
import org.magic.draft.api.card.Cube;
import org.magic.draft.util.Pair;

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

        cube.getCards().shuffleMainboard();

        // [a] 4 packs of 3 cards
        // [b] 4 packs of 7 cards
        // [c] 4 packs of 9 cards
        // [d] 8 packs of 11 cards
        List<CardPack> player1PacksOf11 = createPacks(List.of(0,1, 2, 3, 4, 5, 6, 7), 11);
        List<CardPack> player1PacksOf9 = createPacks(List.of(8, 9, 10, 11), 9);
        List<CardPack> player1PacksOf7 = createPacks(List.of(12, 13, 14, 15), 7);
        List<CardPack> player1PacksOf3 = createPacks(List.of(16, 17, 18, 19), 3);

        List<CardPack> player1Packs = Stream.of(player1PacksOf11, player1PacksOf9, player1PacksOf7, player1PacksOf3)
                                          .flatMap(List::stream)
                                          .collect(Collectors.toList());

        LOGGER.info("Player {} Packs created. {} Packs", player1.getPlayerName(), player1Packs.size());
        
        List<CardPack> player2PacksOf11 = createPacks(List.of(0,1, 2, 3, 4, 5, 6, 7), 11);
        List<CardPack> player2PacksOf9 = createPacks(List.of(8, 9, 10, 11), 9);
        List<CardPack> player2PacksOf7 = createPacks(List.of(12, 13, 14, 15), 7);
        List<CardPack> player2PacksOf3 = createPacks(List.of(16, 17, 18, 19), 3);
        
        List<CardPack> player2Packs = Stream.of(player2PacksOf11, player2PacksOf9, player2PacksOf7, player2PacksOf3)
                                          .flatMap(List::stream)
                                          .collect(Collectors.toList());

        LOGGER.info("Player {} Packs created. {} Packs", player2.getPlayerName(), player2Packs.size());

        Player fullPlayer1 = new Player(player1.getPlayerName(), player1.getPlayerID(), player1Packs, numberOfDoubleDraftPicksPerPlayer, null);
        Player fullPlayer2 = new Player(player2.getPlayerName(), player2.getPlayerID(), player2Packs, numberOfDoubleDraftPicksPerPlayer, null);

        return List.of(fullPlayer1, fullPlayer2);
    }

    private List<CardPack> createPacks(final List<Integer> packNumbers, final int numberOfCardsInPack) {

        List<CardPack> packs = new ArrayList<>();

        for (Integer packNumber : packNumbers) {
            List<Card> cardsInPack = this.cube.getCards().drawCardsFromCube(numberOfCardsInPack);
            packs.add(new CardPack(packNumber, cardsInPack));
        }

        return packs;
    }
    
    public List<CardPack> mergePlayerPacks(final Player player) {

        List<CardPack> elevenCountPacks = new ArrayList<>();
        List<CardPack> nineCountPacks = new ArrayList<>();
        List<CardPack> sevenCountPacks = new ArrayList<>();
        List<CardPack> threeCountPacks = new ArrayList<>();
        
        player.getCardPacks().stream().forEach(pack -> {
            if (pack.getOriginalCardsInPackNumber() == 11) {
                elevenCountPacks.add(pack);
            }
            else if (pack.getOriginalCardsInPackNumber() == 9) {
                nineCountPacks.add(pack);
            }
            else if (pack.getOriginalCardsInPackNumber() == 7) {
                sevenCountPacks.add(pack);
            }
            else if (pack.getOriginalCardsInPackNumber() == 3) {
                threeCountPacks.add(pack);
            }
            else {
                LOGGER.error("Unable to merge Pack {} with {} count of original cards", 
                             pack.getPackNumber(), 
                             pack.getOriginalCardsInPackNumber());
                throw new Error("Unable to merge pack.");
            }
        });

        this.validatePackCounts(8, elevenCountPacks.size());
        this.validatePackCounts(4, nineCountPacks.size());
        this.validatePackCounts(4, sevenCountPacks.size());
        this.validatePackCounts(4, threeCountPacks.size());

        List<CardPack> newPacks = elevenCountPacks;
        newPacks.add(this.mergePacks(9, threeCountPacks));
        newPacks.add(this.mergePacks(10, List.of(sevenCountPacks.get(0), sevenCountPacks.get(1))));
        newPacks.add(this.mergePacks(11, List.of(sevenCountPacks.get(2), sevenCountPacks.get(3))));
        newPacks.add(this.mergePacks(12, List.of(nineCountPacks.get(0), nineCountPacks.get(1))));
        newPacks.add(this.mergePacks(13, List.of(nineCountPacks.get(2), nineCountPacks.get(3))));

        return newPacks;
    }

    private void validatePackCounts(final int expectedPackCount, final int actualPackCount) {
        if (expectedPackCount != actualPackCount) {
            throw new Error("Sorted Pack Count is incorrect for Merging.");
        }
    }

    private CardPack mergePacks(final int packNumber, final List<CardPack> packsToMerge) {
        List<Card> mergedCards = new ArrayList<>();
        packsToMerge.stream().forEach(pack -> mergedCards.addAll(pack.getCardsInPack()));
        return new CardPack(packNumber, mergedCards);
    }
}
