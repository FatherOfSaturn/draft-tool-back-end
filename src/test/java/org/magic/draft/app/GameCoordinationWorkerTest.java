package org.magic.draft.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

import io.smallrye.mutiny.Uni;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.draft.api.GameCreationInfo;
import org.magic.draft.api.GameInfo;
import org.magic.draft.api.Player;
import org.magic.draft.api.card.Cube;
import org.magic.draft.app.GameCoordination.DbHandler;
import org.magic.draft.app.GameCoordination.GameCoordinationWorker;
import org.magic.draft.util.JsonUtility;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GameCoordinationWorkerTest extends TestUtils {

    PackCreator packCreator;

    @Test
    void testGameWorker() throws IOException {

        GameCreationInfo createGameInfo = this.createGameCreationInfo("GameCreationInfo.json");
        Cube adamCube = this.createCubeFromJson("AdamCube.json");

        when(cubeDownloader.getCubeForCubeID(createGameInfo.getCubeID())).thenReturn(Uni.createFrom().item(adamCube));
        when(dbHandler.addGame(any())).thenReturn("GameInfo");

        GameInfo game = gameCoordinationWorker.startGame(createGameInfo).await().atMost(Duration.ofSeconds(3));

//        System.out.println(JsonUtility.getInstance().toJson(game));

//        // Write to file
//        Path outputPath = Paths.get("src/test/resources/UpdatedGame.json");
//        Files.writeString(outputPath, JsonUtility.getInstance().toJson(game), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
//
//        // Optionally, print confirmation
//        System.out.println("Output written to: " + outputPath.toAbsolutePath());
    }

    @Test
    void packMergeTest() throws IOException {

        Cube cube = this.createCubeFromJson("AdamCube.json");
        cube.getCards().shuffleMainboard();

        InputStream gameIS = getClass().getClassLoader().getResourceAsStream("GameCreationInfo.json");
        String gameString = IOUtils.toString(gameIS, "UTF-8");

        GameCreationInfo info = JsonUtility.getInstance().fromJson(gameString, GameCreationInfo.class);

        packCreator = new PackCreator(cube);

        List<Player> players = packCreator.createPyramidPacks(info.getPlayerInfo().get(0), info.getPlayerInfo().get(1), 3);
    }
}