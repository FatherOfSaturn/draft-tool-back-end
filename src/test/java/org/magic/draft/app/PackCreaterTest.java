package org.magic.draft.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.draft.api.GameCreationInfo;
import org.magic.draft.api.Player;
import org.magic.draft.api.card.Cube;
import org.magic.draft.util.JsonUtility;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PackCreaterTest {
    
    PackCreator packCreator;

    @Test
    void packMergeTest() throws IOException {

        Cube cube = this.createCubeFromJson("JoshCube.json");
        cube.getCards().shuffleMainboard();

        InputStream gameIS = getClass().getClassLoader().getResourceAsStream("GameCreationInfo.json");
        String gameString = IOUtils.toString(gameIS, "UTF-8");

        GameCreationInfo info = JsonUtility.getInstance().fromJson(gameString, GameCreationInfo.class);

        packCreator = new PackCreator(cube);

        List<Player> update = packCreator.createPyramidPacks(info.getPlayerInfo().get(0), info.getPlayerInfo().get(1), 3);
    }

    public List<Player> createPlayers() throws IOException {

        Cube cube = this.createCubeFromJson("JoshCube.json");

        InputStream gameIS = getClass().getClassLoader().getResourceAsStream("GameCreationInfo.json");
        String gameString = IOUtils.toString(gameIS, "UTF-8");

        GameCreationInfo info = JsonUtility.getInstance().fromJson(gameString, GameCreationInfo.class);

        packCreator = new PackCreator(cube);

        return packCreator.createPyramidPacks(info.getPlayerInfo().get(0), info.getPlayerInfo().get(1), 3);
    }

    public Cube createCubeFromJson(final String fileName) throws IOException {

        InputStream cubeIS = getClass().getClassLoader().getResourceAsStream(fileName);
        String cubeString = IOUtils.toString(cubeIS, "UTF-8");

        Cube cube = JsonUtility.getInstance().fromJson(cubeString, Cube.class);

        return cube;
    }
}
