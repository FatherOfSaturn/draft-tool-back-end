package org.magic.pyramidDraft.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.common.util.JsonUtility;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.api.Player;
import org.magic.pyramidDraft.api.card.Cube;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
public class GameCoordinationWorkerTest extends TestUtils {

    PackCreator packCreator;

    @Test
    void testGameWorker() throws IOException {
        GameCreationInfo createGameInfo = this.createGameCreationInfo("GameCreationInfo.json");
        Cube adamCube = this.createCubeFromJson("AdamCube.json");

        when(cubeDownloader.getCubeForCubeID(createGameInfo.cubeID())).thenReturn(Uni.createFrom().item(adamCube));
        when(dbHandler.addGame(any())).thenReturn("GameInfo");

        GameInfo game = gameCoordinationWorker.startGame(createGameInfo).await().atMost(Duration.ofSeconds(3));

        assertNotNull(game);
        assertEquals("gameID", game.getGameID());
        assertEquals(GameState.GAME_STARTED, game.getGameState());
        assertNotNull(game.getPlayers());
        assertEquals(2, game.getPlayers().size());
    }

    @Test
    void packMergeTest() throws IOException {
        Cube cube = this.createCubeFromJson("AdamCube.json");
        int initialCardCount = cube.getCards().getMainboard().size();
        assertEquals(540, initialCardCount);

        cube.getCards().shuffleMainboard();

        InputStream gameIS = getClass().getClassLoader().getResourceAsStream("GameCreationInfo.json");
        String gameString = IOUtils.toString(gameIS, "UTF-8");

        GameCreationInfo info = JsonUtility.getInstance().fromJson(gameString, GameCreationInfo.class);

        packCreator = new PackCreator(cube);

        List<Player> players = packCreator.createPyramidPacks(info.playerInfo().get(0), info.playerInfo().get(1), 3);

        assertEquals(2, players.size());

        int expectedPacksPerPlayer = 32;
        assertEquals(expectedPacksPerPlayer, players.get(0).getCardPacks().size());
        assertEquals(expectedPacksPerPlayer, players.get(1).getCardPacks().size());

        int totalCardsConsumed = 0;
        for (int i = 0; i < expectedPacksPerPlayer; i++) {
            totalCardsConsumed += players.get(0).getCardPacks().get(i).getCardsInPack().size();
            totalCardsConsumed += players.get(1).getCardPacks().get(i).getCardsInPack().size();
        }
        assertEquals(480, totalCardsConsumed);
    }
}
