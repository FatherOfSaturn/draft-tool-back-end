package org.magic.draft.app;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.Player;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardPack;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PackMerger {
    private static final Logger LOGGER = LogManager.getLogger(CubeDownloader.class);

    public PackMerger() {}
    
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
