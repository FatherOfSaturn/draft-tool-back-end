package org.magic.draft.app;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.draft.api.card.Cube;
import org.magic.draft.util.JsonUtility;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PackCreaterTest {
    
    PackCreator packCreator;

    @Test
    void packMergeTest() throws IOException {

        // Cube cube = this.createCubeFromJson("FullCube.json");
        // cube.getCards().shuffleMainboard();

        // InputStream gameIS = getClass().getClassLoader().getResourceAsStream("GameCreationInfo.json");
        // String gameString = IOUtils.toString(gameIS, "UTF-8");

        // GameCreationInfo info = JsonUtility.getInstance().fromJson(gameString, GameCreationInfo.class);

        // packCreator = new PackCreator(cube);

        // List<Player> control = packCreator.OLD_createPyramidPacks(info.getPlayerInfo().get(0), info.getPlayerInfo().get(1), 3);

        // cube.getCards().resetIteratorTEST_ONLY();

        // List<Player> update = packCreator.createPlayerBoardsNEW(info.getPlayerInfo().get(0), info.getPlayerInfo().get(1), 3);

        // assertEquals(control.get(0).getCardPacks().size(), update.get(0).getCardPacks().size());
        // assertEquals(control.get(1).getCardPacks().size(), update.get(1).getCardPacks().size());

        // assertEquals(control.get(0).getCardPacks(), update.get(0).getCardPacks());
    }

    public Cube createCubeFromJson(final String fileName) throws IOException {

        InputStream cubeIS = getClass().getClassLoader().getResourceAsStream(fileName);
        String cubeString = IOUtils.toString(cubeIS, "UTF-8");

        Cube cube = JsonUtility.getInstance().fromJson(cubeString, Cube.class);

        return cube;
    }
}
