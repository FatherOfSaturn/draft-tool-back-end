package org.magic.draft.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.Player;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardPack;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PackMerger {
    private static final Logger LOGGER = LogManager.getLogger(PackMerger.class);

    public PackMerger() {}
    
    public List<CardPack> mergePlayerPacks(final Player player) {

        List<CardPack> elevenCountPacks = new ArrayList<>();
        List<CardPack> nineCountPacks = new ArrayList<>();
        List<CardPack> sevenCountPacks = new ArrayList<>();
        List<CardPack> threeCountPacks = new ArrayList<>();
        
        player.getCardPacks().stream().forEach(pack -> {
            switch (pack.getOriginalCardsInPack()) {
                case 11 -> elevenCountPacks.add(pack);
                case 9 -> nineCountPacks.add(pack);
                case 7 -> sevenCountPacks.add(pack);
                case 3 -> threeCountPacks.add(pack);
                default -> {
                    LOGGER.error("Unable to merge Pack {} with {} count of original cards",
                            pack.getPackNumber(),
                            pack.getOriginalCardsInPack());
                    throw new Error("Unable to merge pack.");
                }
            }
        });

        return mergePacksNew(elevenCountPacks, nineCountPacks, sevenCountPacks, threeCountPacks);
    }

    private void validatePackCounts(final int expectedPackCount, final int actualPackCount) {
        if (expectedPackCount != actualPackCount) {
            throw new Error("Sorted Pack Count is incorrect for Merging.");
        }
    }

    private CardPack mergePacks(final int packNumber, final List<CardPack> packsToMerge) {
        List<Card> mergedCards = new ArrayList<>();
        packsToMerge.stream().forEach(pack -> mergedCards.addAll(pack.getCardsInPack()));
        return new CardPack(packNumber, mergedCards, mergedCards.size(), false);
    }

    private List<CardPack> mergePacksNew(final List<CardPack> elevenCountPacks, 
                                      final List<CardPack> nineCountPacks, 
                                      final List<CardPack> sevenCountPacks, 
                                      final List<CardPack> threeCountPacks) {

        List<CardPack> newCardPacks = new ArrayList<>(mergeThreePacks(threeCountPacks));
        AtomicInteger count = new AtomicInteger(newCardPacks.size() - 1);

        for (CardPack elevenCountPack : elevenCountPacks) {
            elevenCountPack.setPackNumber(count.getAndIncrement());
            elevenCountPack.setDoubleDraftedFlag(false);
            newCardPacks.add(elevenCountPack);
        }

        newCardPacks.addAll(this.mergePacksFromPairs(count, sevenCountPacks));
        count.set(newCardPacks.size() - 1);
        newCardPacks.addAll(this.mergePacksFromPairs(count, nineCountPacks));

        return newCardPacks;
    }

    private List<CardPack> mergePacksFromPairs(final AtomicInteger packNumber, final List<CardPack> inputList){
        if (inputList.size() % 2 != 0) {
            throw new IllegalArgumentException("Input list must contain an even number of elements.");
        }

        List<CardPack> newPacks = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i += 2) {
            newPacks.add(this.mergePacks(packNumber.getAndIncrement(), List.of(inputList.get(i),
                                                                               inputList.get(i+1))));
        }

        return newPacks;
    }

    private List<CardPack> mergeThreePacks(final List<CardPack> threeCountPacks) {

        switch (threeCountPacks.size()) {
            case 4:
            case 6:
                return List.of(this.mergePacks(0, threeCountPacks));
            case 8:
                // Merge first 4 and last 4
                return List.of(this.mergePacks(0, List.of(threeCountPacks.get(0),
                                                                     threeCountPacks.get(1),
                                                                     threeCountPacks.get(2),
                                                                     threeCountPacks.get(3))),
                               this.mergePacks(1, List.of(threeCountPacks.get(4),
                                                                     threeCountPacks.get(5),
                                                                     threeCountPacks.get(6),
                                                                     threeCountPacks.get(7)))
                );
            default:
                LOGGER.error("Unable to merge packs of 3 with, {} number of packs", threeCountPacks.size());
                throw new Error("Sorted Pack Count is incorrect for Merging.");
        }
    }
    /*
     *         
     * if (cubeSize <= 540 && cubeSize > 490) {
            //large cube
            packsOf3 = 8; 16
            packsOf7 = 8;
            packsOf9 = 8;
            packsOf11 = 8;
        }
        else if (cubeSize <= 490 && cubeSize > 410) {
            // medium
            packsOf3 = 6; 12
            packsOf7 = 4;
            packsOf9 = 8;
            packsOf11 = 8;
        }
        else if (cubeSize <= 410 && cubeSize > 328) {
            // small
            packsOf3 = 4; 8
            packsOf7 = 4;
            packsOf9 = 4;
            packsOf11 = 8;
        }
     */
}
