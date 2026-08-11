package org.magic.classicDraft.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.classicDraft.api.ClassicPlayer;
import org.magic.classicDraft.api.ClassicPlayerCreationInfo;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardPack;
import org.magic.pyramidDraft.api.card.Cube;

import jakarta.inject.Inject;

public class ClassicPackCreator {
    private static final Logger LOGGER = LogManager.getLogger(ClassicPackCreator.class);

    private final Cube cube;
    private final int cardsPerPack;
    private final int packsPerPlayer;

    @Inject
    public ClassicPackCreator(final Cube cube, final int cardsPerPack, final int packsPerPlayer) {
        this.cube = Objects.requireNonNull(cube, "cube Required for ClassicPackCreator");
        this.cardsPerPack = cardsPerPack;
        this.packsPerPlayer = packsPerPlayer;
    }

    public List<ClassicPlayer> createClassicPacks(final List<ClassicPlayerCreationInfo> playerInfos) {
        int totalCardsNeeded = playerInfos.size() * packsPerPlayer * cardsPerPack;
        int cubeSize = cube.getCards().getMainboard().size();
        if (totalCardsNeeded > cubeSize) {
            throw new IllegalArgumentException("Not enough cards in cube. Need " + totalCardsNeeded
                    + " but cube only has " + cubeSize + " cards.");
        }

        cube.getCards().shuffleMainboard();

        List<ClassicPlayer> players = new ArrayList<>();
        for (int i = 0; i < playerInfos.size(); i++) {
            ClassicPlayerCreationInfo info = playerInfos.get(i);
            List<CardPack> dealtPacks = createPlayerDealtPacks(i, packsPerPlayer);
            ClassicPlayer player = new ClassicPlayer(
                    info.playerName(),
                    info.accountID(),
                    i,
                    dealtPacks,
                    null,
                    null);
            players.add(player);
        }

        return players;
    }

    private List<CardPack> createPlayerDealtPacks(final int playerIndex, final int packCount) {
        List<CardPack> packs = new ArrayList<>();
        for (int p = 0; p < packCount; p++) {
            int packNumber = playerIndex * packCount + p;
            List<Card> cards = cube.getCards().drawCardsFromCube(cardsPerPack);
            packs.add(new CardPack(packNumber, cards, cardsPerPack, false));
        }
        return packs;
    }
}
