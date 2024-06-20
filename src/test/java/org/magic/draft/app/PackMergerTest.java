package org.magic.draft.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.draft.api.Player;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardPack;
import org.magic.draft.util.JsonUtility;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PackMergerTest {

    @InjectMocks
    PackMerger packMerger;

    // @Test
    void packMergeTest() throws IOException {

        // Test needs to add data for the merge flag being true

        InputStream player1IS = getClass().getClassLoader().getResourceAsStream("Player1.json");
        String player1String = IOUtils.toString(player1IS, "UTF-8");

        Player player1 = JsonUtility.getInstance().fromJson(player1String, Player.class);

        InputStream player2IS = getClass().getClassLoader().getResourceAsStream("Player2.json");
        String player2String = IOUtils.toString(player2IS, "UTF-8");

        Player player2 = JsonUtility.getInstance().fromJson(player2String, Player.class);

        this.draftCardInEveryPack(player1);
        this.draftCardInEveryPack(player2);

        List<CardPack> mergedPlayer1Packs = packMerger.mergePlayerPacks(player1);
        List<CardPack> mergedPlayer2Packs = packMerger.mergePlayerPacks(player2);

        assertEquals(13, mergedPlayer1Packs.size());
        assertEquals(13, mergedPlayer2Packs.size());
    }

    void draftCardInEveryPack(final Player player) {
        player.getCardPacks().stream().forEach(pack -> {
            Card card = pack.getCardsInPack().stream().findFirst().get();
            player.draftCard(card.getCardID(), pack.getPackNumber() - 1, false);
        });
        
        assertEquals(20, player.getCardsDrafted().size());
    }
}