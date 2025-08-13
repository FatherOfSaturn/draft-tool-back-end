package org.magic.draft.app;

import io.smallrye.mutiny.Uni;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.draft.api.GameCreationInfo;
import org.magic.draft.api.GameInfo;
import org.magic.draft.api.card.Cube;
import org.magic.draft.app.GameCoordination.DbHandler;
import org.magic.draft.app.GameCoordination.GameCoordinationWorker;
import org.magic.draft.util.JsonUtility;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public abstract class TestUtils {

    @Mock
    DbHandler dbHandler;
    @Mock
    PackMerger packMerger;
    @Mock
    CubeDownloader cubeDownloader;

    GameCoordinationWorker gameCoordinationWorker;

    @BeforeEach
    void setup() {
        gameCoordinationWorker = new GameCoordinationWorker(dbHandler, packMerger, cubeDownloader);
    }

    public Cube createCubeFromJson(final String fileName) throws IOException {

        InputStream cubeIS = getClass().getClassLoader().getResourceAsStream(fileName);
        String cubeString = IOUtils.toString(cubeIS, "UTF-8");

        Cube cube = JsonUtility.getInstance().fromJson(cubeString, Cube.class);

        return cube;
    }

    public GameCreationInfo createGameCreationInfo(final String fileName) throws IOException {

        InputStream gameIS = getClass().getClassLoader().getResourceAsStream("GameCreationInfo.json");
        String gameString = IOUtils.toString(gameIS, "UTF-8");

        GameCreationInfo info = JsonUtility.getInstance().fromJson(gameString, GameCreationInfo.class);

        return info;
    }

    public GameInfo createGameInfo(final String cubeName) throws IOException {

        GameCreationInfo createGameInfo = this.createGameCreationInfo("GameCreationInfo.json");
        Cube adamCube = this.createCubeFromJson(cubeName);

        when(cubeDownloader.getCubeForCubeID(createGameInfo.getCubeID())).thenReturn(Uni.createFrom().item(adamCube));
        when(dbHandler.addGame(any())).thenReturn("GameInfo");

        return gameCoordinationWorker.startGame(createGameInfo).await().atMost(Duration.ofSeconds(3));
    }
}