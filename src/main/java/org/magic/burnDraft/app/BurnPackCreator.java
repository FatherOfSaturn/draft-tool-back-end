package org.magic.burnDraft.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.burnDraft.api.BurnPlayer;
import org.magic.burnDraft.api.BurnPlayerCreationInfo;
import org.magic.common.card.Card;
import org.magic.common.card.CardPack;
import org.magic.common.card.Cube;

import jakarta.inject.Inject;

/**
 * Deals the initial packs for a burn draft, mirroring {@code ClassicPackCreator}.
 * Validates that the cube has enough cards for every player's packs.
 */
public class BurnPackCreator {
    private static final Logger LOGGER = LogManager.getLogger(BurnPackCreator.class);

    private final Cube cube;
    private final int cardsPerPack;
    private final int packsPerPlayer;

    @Inject
    public BurnPackCreator(final Cube cube, final int cardsPerPack, final int packsPerPlayer) {
        this.cube = Objects.requireNonNull(cube, "cube Required for BurnPackCreator");
        this.cardsPerPack = cardsPerPack;
        this.packsPerPlayer = packsPerPlayer;
    }

    /**
     * Deals {@code packsPerPlayer} packs of {@code cardsPerPack} cards to every player.
     *
     * @param playerInfos the players in draft-seat order
     * @return the players with their dealt packs
     * @throws IllegalArgumentException when the cube does not contain enough cards
     */
    public List<BurnPlayer> createBurnPacks(final List<BurnPlayerCreationInfo> playerInfos) {
        int totalCardsNeeded = playerInfos.size() * packsPerPlayer * cardsPerPack;
        int cubeSize = cube.getCards().getMainboard().size();
        if (totalCardsNeeded > cubeSize) {
            throw new IllegalArgumentException("Not enough cards in cube. Need " + totalCardsNeeded
                    + " but cube only has " + cubeSize + " cards.");
        }

        cube.getCards().shuffleMainboard();

        List<BurnPlayer> players = new ArrayList<>();
        for (int i = 0; i < playerInfos.size(); i++) {
            BurnPlayerCreationInfo info = playerInfos.get(i);
            List<CardPack> dealtPacks = createPlayerDealtPacks(i, packsPerPlayer);
            BurnPlayer player = new BurnPlayer(
                    info.playerName(),
                    info.accountID(),
                    i,
                    dealtPacks,
                    null,
                    null,
                    null);
            player.setPlayerToken(info.playerToken());
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
