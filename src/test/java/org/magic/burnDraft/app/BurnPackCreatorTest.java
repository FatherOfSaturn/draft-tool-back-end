package org.magic.burnDraft.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.TestJson;
import org.magic.burnDraft.api.BurnPlayer;
import org.magic.burnDraft.api.BurnPlayerCreationInfo;
import org.magic.common.card.CardPack;
import org.magic.common.card.Cube;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BurnPackCreatorTest {

    @Test
    void shouldDealConfiguredPacksToEveryPlayer() throws IOException {
        Cube cube = createCubeFromJson("JoshCube.json");
        assertEquals(360, cube.getCards().getMainboard().size());

        List<BurnPlayerCreationInfo> infos = List.of(
                new BurnPlayerCreationInfo("Alice", "acct-1", null),
                new BurnPlayerCreationInfo("Bob", "acct-2", null),
                new BurnPlayerCreationInfo("Charlie", "acct-3", null),
                new BurnPlayerCreationInfo("Diana", "acct-4", null));

        int cardsPerPack = 4;
        int packsPerPlayer = 3;
        BurnPackCreator creator = new BurnPackCreator(cube, cardsPerPack, packsPerPlayer);
        List<BurnPlayer> players = creator.createBurnPacks(infos);

        assertEquals(4, players.size());
        int totalCardsConsumed = 0;
        for (int i = 0; i < players.size(); i++) {
            BurnPlayer player = players.get(i);
            assertEquals(infos.get(i).playerName(), player.getPlayerName());
            assertEquals(infos.get(i).accountID(), player.getAccountID());
            assertEquals(i, player.getDraftOrderNumber());
            assertEquals(packsPerPlayer, player.getDealtCardPacks().size());
            assertTrue(player.getActiveCardPacks().isEmpty());
            assertTrue(player.getCardsDrafted().isEmpty());
            assertTrue(player.getCardsBurned().isEmpty());
            for (CardPack pack : player.getDealtCardPacks()) {
                assertEquals(cardsPerPack, pack.getCardsInPack().size());
                assertEquals(cardsPerPack, pack.getOriginalCardsInPack());
                totalCardsConsumed += pack.getCardsInPack().size();
            }
        }
        assertEquals(4 * 3 * 4, totalCardsConsumed);
    }

    @Test
    void shouldSupportTwoPlayerGames() throws IOException {
        Cube cube = createCubeFromJson("JoshCube.json");
        BurnPackCreator creator = new BurnPackCreator(cube, 6, 1);

        List<BurnPlayer> players = creator.createBurnPacks(List.of(
                new BurnPlayerCreationInfo("Alice", "acct-1", null),
                new BurnPlayerCreationInfo("Bob", null, null)));

        assertEquals(2, players.size());
        assertNull(players.get(1).getAccountID());
        assertEquals(1, players.get(0).getDealtCardPacks().size());
    }

    @Test
    void shouldRejectWhenCubeDoesNotHaveEnoughCards() throws IOException {
        Cube cube = createCubeFromJson("JoshCube.json");
        List<BurnPlayerCreationInfo> infos = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            infos.add(new BurnPlayerCreationInfo("Player" + i, "acct-" + i, null));
        }

        BurnPackCreator creator = new BurnPackCreator(cube, 25, 4);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> creator.createBurnPacks(infos));
        assertTrue(ex.getMessage().contains("Not enough cards in cube"), ex.getMessage());
    }

    @Test
    void shouldPreserveTokens() throws IOException {
        Cube cube = createCubeFromJson("JoshCube.json");
        BurnPackCreator creator = new BurnPackCreator(cube, 3, 1);

        List<BurnPlayer> players = creator.createBurnPacks(List.of(
                new BurnPlayerCreationInfo("Alice", "acct-1", "tok-a"),
                new BurnPlayerCreationInfo("Bob", "acct-2", "tok-b")));

        assertEquals("tok-a", players.get(0).getPlayerToken());
        assertEquals("tok-b", players.get(1).getPlayerToken());
    }

    private Cube createCubeFromJson(final String fileName) throws IOException {
        try (InputStream cubeIS = getClass().getClassLoader().getResourceAsStream(fileName)) {
            String cubeString = IOUtils.toString(cubeIS, "UTF-8");
            return TestJson.fromJson(cubeString, Cube.class);
        }
    }
}
