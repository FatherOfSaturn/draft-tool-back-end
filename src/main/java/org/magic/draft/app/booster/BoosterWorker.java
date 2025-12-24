package org.magic.draft.app.booster;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.magic.draft.api.card.CardPack;
import org.magic.draft.api.card.Cube;
import org.magic.draft.app.CubeDownloader;
import org.magic.draft.external.ScryfallService;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BoosterWorker {
    private static final Logger LOGGER = LogManager.getLogger(BoosterWorker.class);

    private final CubeDownloader cubeDownloader;
    private final ScryfallService scryfall;

    @Inject
    public BoosterWorker(@RestClient final ScryfallService scryfall,
                                     final CubeDownloader cubeDownloader) {
        this.cubeDownloader = cubeDownloader;
        this.scryfall = scryfall;
    }

    public Uni<Void> makePacksFromSet(final String setName) {

        scryfall.getBoosterForSet(setName);
        // find set from string//id
        // add scryfall api and calls for that

        // 1 basic land
        // 10 commons
        // 3 uncommon
        // 1 rare / mythic rare
        // possible for 1 common to be replaced by a foil of any rarity

        // a mythic appears 12.5% of the time (1 in 8 packs)
        // foil happens in 33% of packs

        return null;
    }

    public Uni<List<CardPack>> makeDraftPacksFromCube(final String cubeID,
                                            final int numberOfPacks,
                                            final int numberOfCardsInPack) {

        return cubeDownloader.getCubeForCubeID(cubeID)
                      .map(cube -> this.getCards(cube, 
                                                 numberOfPacks, 
                                                 numberOfCardsInPack));
    }

    private List<CardPack> getCards(final Cube cube,
                                    final int numberOfPacks,
                                    final int numberOfCardsInPack) {

        if (numberOfPacks * numberOfCardsInPack > cube.getCards().getMainboard().size()) {
            LOGGER.error("Total Cards in Cube: {}, Attempting to make {} packs, with {} in each. This would require {} cards in the Cube.",
                         cube.getCards().getMainboard().size(),
                         numberOfPacks,
                         numberOfCardsInPack,
                         numberOfPacks * numberOfCardsInPack);
            throw new Error("Attempting to create more packs than there are cards in the cube.");
        }
        
        cube.getCards().shuffleMainboard();
        List<CardPack> packs = new ArrayList<>();

        for (int packNumber = 0; packNumber < numberOfPacks; packNumber++) {
            packs.add(new CardPack(packNumber,
                                            cube.getCards().drawCardsFromCube(numberOfCardsInPack), 
                                            numberOfCardsInPack, 
                                            false));
        }

        return packs;
    }
}