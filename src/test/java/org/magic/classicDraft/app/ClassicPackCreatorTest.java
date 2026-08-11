package org.magic.classicDraft.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.classicDraft.api.ClassicPlayer;
import org.magic.classicDraft.api.ClassicPlayerCreationInfo;
import org.magic.pyramidDraft.api.card.CardPack;
import org.magic.pyramidDraft.api.card.Cube;
import org.magic.common.util.JsonUtility;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassicPackCreatorTest {

    @Test
    void testCreateClassicPacksFourPlayers() throws IOException {
        Cube cube = createCubeFromJson("JoshCube.json");
        int cubeSize = cube.getCards().getMainboard().size();
        assertEquals(360, cubeSize);

        List<ClassicPlayerCreationInfo> infos = List.of(
                new ClassicPlayerCreationInfo("Alice", "acct-1"),
                new ClassicPlayerCreationInfo("Bob", "acct-2"),
                new ClassicPlayerCreationInfo("Charlie", "acct-3"),
                new ClassicPlayerCreationInfo("Diana", "acct-4"));

        int cardsPerPack = 4;
        int packsPerPlayer = 3;
        ClassicPackCreator creator = new ClassicPackCreator(cube, cardsPerPack, packsPerPlayer);
        List<ClassicPlayer> players = creator.createClassicPacks(infos);

        assertEquals(4, players.size());
        for (int i = 0; i < 4; i++) {
            ClassicPlayer p = players.get(i);
            assertEquals(infos.get(i).playerName(), p.getPlayerName());
            assertEquals(infos.get(i).accountID(), p.getAccountID());
            assertEquals(i, p.getDraftOrderNumber());
            assertEquals(packsPerPlayer, p.getDealtCardPacks().size());
            assertEquals(0, p.getActiveCardPacks().size());
            assertEquals(0, p.getCardsDrafted().size());
        }

        int totalCardsConsumed = 0;
        for (ClassicPlayer p : players) {
            for (int j = 0; j < packsPerPlayer; j++) {
                totalCardsConsumed += p.getDealtCardPacks().get(j).getCardsInPack().size();
                assertEquals(cardsPerPack, p.getDealtCardPacks().get(j).getOriginalCardsInPack());
            }
        }
        assertEquals(4 * 3 * 4, totalCardsConsumed);
        assertEquals(48, totalCardsConsumed);
    }

    @Test
    void testCreateClassicPacksTwelvePlayers() throws IOException {
        Cube cube = createCubeFromJson("AdamCube.json");

        List<ClassicPlayerCreationInfo> infos = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            infos.add(new ClassicPlayerCreationInfo("Player" + i, "acct-" + i));
        }

        int cardsPerPack = 12;
        int packsPerPlayer = 2;
        ClassicPackCreator creator = new ClassicPackCreator(cube, cardsPerPack, packsPerPlayer);
        List<ClassicPlayer> players = creator.createClassicPacks(infos);

        assertEquals(12, players.size());
        int totalCards = 12 * 2 * 12;
        assertEquals(288, totalCards);
        assertTrue(totalCards <= cube.getCards().getMainboard().size(),
                "Must have enough cards in cube");
    }

    @Test
    void testCannotCreateWhenNotEnoughCards() throws IOException {
        Cube cube = createCubeFromJson("JoshCube.json");

        List<ClassicPlayerCreationInfo> infos = List.of(
                new ClassicPlayerCreationInfo("Alice", "acct-1"),
                new ClassicPlayerCreationInfo("Bob", "acct-2"),
                new ClassicPlayerCreationInfo("Charlie", "acct-3"),
                new ClassicPlayerCreationInfo("Diana", "acct-4"));

        ClassicPackCreator creator = new ClassicPackCreator(cube, 25, 4);

        assertThrows(IllegalArgumentException.class, () -> {
            creator.createClassicPacks(infos);
        });
    }

    @Test
    void testPlayerNamesAndAccountIdsArePreserved() throws IOException {
        Cube cube = createCubeFromJson("JoshCube.json");

        List<ClassicPlayerCreationInfo> infos = List.of(
                new ClassicPlayerCreationInfo("Alice", null),
                new ClassicPlayerCreationInfo("Bob", "acct-bob"));

        ClassicPackCreator creator = new ClassicPackCreator(cube, 4, 3);
        List<ClassicPlayer> players = creator.createClassicPacks(infos);

        assertNull(players.get(0).getAccountID());
        assertEquals("Alice", players.get(0).getPlayerName());
        assertEquals("Bob", players.get(1).getPlayerName());
        assertEquals("acct-bob", players.get(1).getAccountID());
    }

    @Test
    void testCubeCardCountValidation() throws IOException {
        Cube cube = createCubeFromJson("JoshCube.json");

        List<ClassicPlayerCreationInfo> infos = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            infos.add(new ClassicPlayerCreationInfo("Player" + i, "acct-" + i));
        }

        ClassicPackCreator creator = new ClassicPackCreator(cube, 8, 6);

        assertThrows(IllegalArgumentException.class, () -> {
            creator.createClassicPacks(infos);
        });
    }

    protected Cube createCubeFromJson(final String fileName) throws IOException {
        InputStream cubeIS = getClass().getClassLoader().getResourceAsStream(fileName);
        String cubeString = IOUtils.toString(cubeIS, "UTF-8");
        return JsonUtility.getInstance().fromJson(cubeString, Cube.class);
    }
}
