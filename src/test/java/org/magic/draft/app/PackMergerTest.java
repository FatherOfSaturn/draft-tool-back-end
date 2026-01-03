package org.magic.draft.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.draft.api.GameInfo;
import org.magic.draft.api.Player;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardPack;
import org.magic.draft.api.card.Cube;
import org.magic.draft.util.JsonUtility;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PackMergerTest extends TestUtils {

    @InjectMocks
    PackMerger packMerger;

    @Test
    void packMergeTest() throws IOException {

        InputStream cubeIS = getClass().getClassLoader().getResourceAsStream("AdamCube.json");
        String cubeString = IOUtils.toString(cubeIS, "UTF-8");

        Cube cube = JsonUtility.getInstance().fromJson(cubeString, Cube.class);
        PackCreator creator = new PackCreator(cube);

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

        assertEquals(18, mergedPlayer1Packs.size());
        assertEquals(18, mergedPlayer2Packs.size());
    }

    @Test
    void packMergeTestNew() throws IOException {

        GameInfo game = this.createGameInfo("JoshCube.json");

        this.draftCardInEveryPack(game.getPlayers().get(0));
        this.draftCardInEveryPack(game.getPlayers().get(1));

        List<CardPack> mergedPlayer1Packs = packMerger.mergePlayerPacks(game.getPlayers().get(0));
        List<CardPack> mergedPlayer2Packs = packMerger.mergePlayerPacks(game.getPlayers().get(1));

        assertEquals(13, mergedPlayer1Packs.size());
        assertEquals(13, mergedPlayer2Packs.size());
    }

    void draftCardInEveryPack(final Player player) {
        player.getCardPacks().stream().forEach(pack -> {
            Card card = pack.getCardsInPack().stream().findFirst().get();
            player.draftCard(card.getCardID(), pack.getPackNumber(), false);
        });

        assertEquals(player.getCardPacks().size(), player.getCardsDrafted().size());
    }
}