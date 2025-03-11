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

        this.validatePackCounts(8, elevenCountPacks.size());
        this.validatePackCounts(4, nineCountPacks.size());
        this.validatePackCounts(4, sevenCountPacks.size());
        this.validatePackCounts(4, threeCountPacks.size());


        List<CardPack> newPacks = new ArrayList<>();
        newPacks.add(this.mergePacks(0, threeCountPacks));

        AtomicInteger count = new AtomicInteger(0);
        List<Integer> newPackNumbers = List.of(1, 2, 3, 4, 5, 6, 7, 8);

        newPacks.addAll(elevenCountPacks.stream().peek(pack -> {
                LOGGER.info(count.get());
                pack.setPackNumber(newPackNumbers.get(count.getAndIncrement()));
                pack.setDoubleDraftedFlag(false);
            }).collect(Collectors.toList()));

        newPacks.add(this.mergePacks(9, List.of(sevenCountPacks.get(0), sevenCountPacks.get(1))));
        newPacks.add(this.mergePacks(10, List.of(sevenCountPacks.get(2), sevenCountPacks.get(3))));
        newPacks.add(this.mergePacks(11, List.of(nineCountPacks.get(0), nineCountPacks.get(1))));
        newPacks.add(this.mergePacks(12, List.of(nineCountPacks.get(2), nineCountPacks.get(3))));

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
        return new CardPack(packNumber, mergedCards, mergedCards.size(), false);
    }
}
