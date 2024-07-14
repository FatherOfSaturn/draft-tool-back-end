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
        List<CardPack> player1PacksOf3 = createPacks(List.of(0, 1, 2, 3), 3);
        List<CardPack> player1PacksOf7 = createPacks(List.of(4, 5, 6, 7), 7);
        List<CardPack> player1PacksOf9 = createPacks(List.of(8, 9, 10, 11), 9);
        List<CardPack> player1PacksOf11 = createPacks(List.of(12,13, 14, 15, 16, 17, 18, 19), 11);

        List<CardPack> player1Packs = Stream.of(player1PacksOf3, player1PacksOf7, player1PacksOf9, player1PacksOf11)
                                          .flatMap(List::stream)
                                          .collect(Collectors.toList());

        LOGGER.info("Player {} Packs created. {} Packs", player1.getPlayerName(), player1Packs.size());

        List<CardPack> player2PacksOf3 = createPacks(List.of(0, 1, 2, 3), 3);
        List<CardPack> player2PacksOf7 = createPacks(List.of(4, 5, 6, 7), 7);
        List<CardPack> player2PacksOf9 = createPacks(List.of(8, 9, 10, 11), 9);
        List<CardPack> player2PacksOf11 = createPacks(List.of(12,13, 14, 15, 16, 17, 18, 19), 11);
        
        List<CardPack> player2Packs = Stream.of(player2PacksOf3, player2PacksOf7, player2PacksOf9, player2PacksOf11)
                                          .flatMap(List::stream)
                                          .collect(Collectors.toList());

        LOGGER.info("Player {} Packs created. {} Packs", player2.getPlayerName(), player2Packs.size());

        Player fullPlayer1 = new Player(player1.getPlayerName(), player1.getPlayerID(), player1Packs, numberOfDoubleDraftPicksPerPlayer, null, 0, false);
        Player fullPlayer2 = new Player(player2.getPlayerName(), player2.getPlayerID(), player2Packs, numberOfDoubleDraftPicksPerPlayer, null, 0, false);

        return List.of(fullPlayer1, fullPlayer2);
    }

    private List<CardPack> createPacks(final List<Integer> packNumbers, final int numberOfCardsInPack) {

        List<CardPack> packs = new ArrayList<>();

        for (Integer packNumber : packNumbers) {
            List<Card> cardsInPack = this.cube.getCards().drawCardsFromCube(numberOfCardsInPack);
            packs.add(new CardPack(packNumber, cardsInPack, numberOfCardsInPack, false));
        }

        return packs;
    }
}
