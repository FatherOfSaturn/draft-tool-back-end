package org.magic.pyramidDraft.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.pyramidDraft.api.Player;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardPack;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Handles merging of card packs between draft phases in a pyramid draft.
 * After a player finishes drafting all their packs, the remaining packs are merged
 * by size and swapped with the other player's packs for the next draft phase.
 */
@ApplicationScoped
public class PackMerger {
    private static final Logger LOGGER = LogManager.getLogger(PackMerger.class);

    public PackMerger() {}
    
    /**
     * Merges a player's remaining packs by grouping them by size (3, 7, 9, 11 cards),
     * then applies the merging rules to produce a consolidated list of packs for the
     * next draft phase.
     *
     * @param player the player whose packs to merge
     * @return the list of merged packs, ready to be swapped with the other player
     */
    public List<CardPack> mergePlayerPacks(final Player player) {

        List<CardPack> elevenCountPacks = new ArrayList<>();
        List<CardPack> nineCountPacks = new ArrayList<>();
        List<CardPack> sevenCountPacks = new ArrayList<>();
        List<CardPack> threeCountPacks = new ArrayList<>();
        
        player.getCardPacks().forEach(pack -> {
            switch (pack.getOriginalCardsInPack()) {
                case 11 -> elevenCountPacks.add(pack);
                case 9 -> nineCountPacks.add(pack);
                case 7 -> sevenCountPacks.add(pack);
                case 3 -> threeCountPacks.add(pack);
                default -> {
                    LOGGER.error("Unable to merge Pack {} with {} count of original cards",
                            pack.getPackNumber(),
                            pack.getOriginalCardsInPack());
                    throw new IllegalStateException("Unable to merge pack.");
                }
            }
        });

        return mergePacksNew(elevenCountPacks, nineCountPacks, sevenCountPacks, threeCountPacks);
    }

    private CardPack mergePacks(final int packNumber, final List<CardPack> packsToMerge) {
        List<Card> mergedCards = new ArrayList<>();
        packsToMerge.forEach(pack -> mergedCards.addAll(pack.getCardsInPack()));
        return new CardPack(packNumber, mergedCards, mergedCards.size(), false);
    }

    /**
     * Merges packs by combining them according to pyramid draft rules:
     * <ul>
     *   <li>Packs of 3 are merged first (4/6 → 1 pack, 8 → 2 packs of 4)</li>
     *   <li>Packs of 11 are kept as-is (renumbered)</li>
     *   <li>Packs of 7 are merged in pairs</li>
     *   <li>Packs of 9 are merged in pairs</li>
     * </ul>
     */
    private List<CardPack> mergePacksNew(final List<CardPack> elevenCountPacks, 
                                      final List<CardPack> nineCountPacks, 
                                      final List<CardPack> sevenCountPacks, 
                                      final List<CardPack> threeCountPacks) {

        List<CardPack> newCardPacks = new ArrayList<>(mergeThreePacks(threeCountPacks));
        AtomicInteger count = new AtomicInteger(newCardPacks.size());

        for (CardPack elevenCountPack : elevenCountPacks) {
            elevenCountPack.setPackNumber(count.getAndIncrement());
            elevenCountPack.setDoubleDraftedFlag(false);
            newCardPacks.add(elevenCountPack);
        }

        newCardPacks.addAll(this.mergePacksFromPairs(count, sevenCountPacks));
        count.set(newCardPacks.size());
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

    /**
     * Merges packs of 3 cards into larger packs. The merging strategy depends on the count:
     * <ul>
     *   <li>4 or 6 packs → all merged into a single pack</li>
     *   <li>8 packs → split into two groups of 4, each merged separately</li>
     * </ul>
     *
     * @param threeCountPacks the list of packs containing 3 cards each
     * @return the merged packs
     * @throws IllegalStateException if the pack count is not 4, 6, or 8
     */
    private List<CardPack> mergeThreePacks(final List<CardPack> threeCountPacks) {

        switch (threeCountPacks.size()) {
            case 4, 6 -> {
                return List.of(this.mergePacks(0, threeCountPacks));
            }
            case 8 -> {
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
            }
            default -> {
                LOGGER.error("Unable to merge packs of 3 with, {} number of packs", threeCountPacks.size());
                throw new IllegalStateException("Sorted Pack Count is incorrect for Merging.");
            }
        }
    }
}
