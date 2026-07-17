package org.magic.pyramidDraft.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.common.util.JsonUtility;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.Player;
import org.magic.pyramidDraft.api.card.Cube;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PackCreatorTest {

    PackCreator packCreator;

    @Test
    void packMergeTest() throws IOException {
        Cube cube = createCubeFromJson("JoshCube.json");
        int initialCardCount = cube.getCards().getMainboard().size();
        assertEquals(360, initialCardCount);

        cube.getCards().shuffleMainboard();

        InputStream gameIS = getClass().getClassLoader().getResourceAsStream("GameCreationInfo.json");
        String gameString = IOUtils.toString(gameIS, "UTF-8");

        GameCreationInfo info = JsonUtility.getInstance().fromJson(gameString, GameCreationInfo.class);

        packCreator = new PackCreator(cube);

        List<Player> players = packCreator.createPyramidPacks(info.playerInfo().get(0), info.playerInfo().get(1), 3);

        assertEquals(2, players.size());

        Player player1 = players.get(0);
        Player player2 = players.get(1);

        assertEquals("Josh", player1.getPlayerName());
        assertEquals("Zach", player2.getPlayerName());

        int expectedPacksPerPlayer = 20;
        assertEquals(expectedPacksPerPlayer, player1.getCardPacks().size());
        assertEquals(expectedPacksPerPlayer, player2.getCardPacks().size());

        int totalCardsConsumed = 0;
        for (int i = 0; i < expectedPacksPerPlayer; i++) {
            totalCardsConsumed += player1.getCardPacks().get(i).getCardsInPack().size();
            totalCardsConsumed += player2.getCardPacks().get(i).getCardsInPack().size();
        }
        assertEquals(328, totalCardsConsumed);
    }

    protected Cube createCubeFromJson(final String fileName) throws IOException {
        InputStream cubeIS = getClass().getClassLoader().getResourceAsStream(fileName);
        String cubeString = IOUtils.toString(cubeIS, "UTF-8");

        return JsonUtility.getInstance().fromJson(cubeString, Cube.class);
    }
}
