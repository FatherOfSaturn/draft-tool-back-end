package org.magic.pyramidDraft.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.common.util.JsonUtility;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.api.GameStatusMessage;
import org.magic.pyramidDraft.api.Player;
import org.magic.pyramidDraft.api.card.Cube;
import org.magic.pyramidDraft.api.card.CardPack;
import org.magic.pyramidDraft.api.card.Card;
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

    @Test
    void shouldThrowWhenMergeGameHasWrongPlayerCount() {
        GameInfo game = new GameInfo("gid", "cid", List.of(), GameState.GAME_STARTED, Instant.now());

        when(dbHandler.findGame("gid")).thenReturn(game);

        assertThrows(IllegalStateException.class, () ->
                gameCoordinationWorker.mergeAndSwapPacks("gid"));
    }

    @Test
    void shouldReturnAlreadyMergedWhenMergeCalledOnMergedGame() {
        var p1 = mock(Player.class);
        var p2 = mock(Player.class);
        GameInfo game = new GameInfo("gid", "cid", List.of(p1, p2), GameState.GAME_MERGED, Instant.now());

        when(dbHandler.findGame("gid")).thenReturn(game);

        GameStatusMessage result = gameCoordinationWorker.mergeAndSwapPacks("gid").await().indefinitely();

        assertEquals(GameState.GAME_MERGED, result.gameState());
    }

    @Test
    void shouldMergeWhenBothPlayersReady() {
        var p1 = mock(Player.class);
        when(p1.isReadyForMerge()).thenReturn(true);
        var p2 = mock(Player.class);
        when(p2.isReadyForMerge()).thenReturn(true);
        var packs1 = List.of(mock(CardPack.class));
        var packs2 = List.of(mock(CardPack.class));
        when(packMerger.mergePlayerPacks(p1)).thenReturn(packs2);
        when(packMerger.mergePlayerPacks(p2)).thenReturn(packs1);

        GameInfo game = new GameInfo("gid", "cid", List.of(p1, p2), GameState.GAME_STARTED, Instant.now());
        when(dbHandler.findGame("gid")).thenReturn(game);

        GameStatusMessage result = gameCoordinationWorker.mergeAndSwapPacks("gid").await().indefinitely();

        assertEquals(GameState.GAME_MERGED, result.gameState());
    }

    @Test
    void shouldReturnStartedWhenNotReadyForMerge() {
        var p1 = mock(Player.class);
        when(p1.isReadyForMerge()).thenReturn(false);
        var p2 = mock(Player.class);

        GameInfo game = new GameInfo("gid", "cid", List.of(p1, p2), GameState.GAME_STARTED, Instant.now());
        when(dbHandler.findGame("gid")).thenReturn(game);

        GameStatusMessage result = gameCoordinationWorker.mergeAndSwapPacks("gid").await().indefinitely();

        assertEquals(GameState.GAME_STARTED, result.gameState());
    }

    @Test
    void shouldEndGameWhenNotComplete() {
        GameInfo game = new GameInfo("gid", "cid", List.of(mock(Player.class), mock(Player.class)), GameState.GAME_STARTED, Instant.now());

        when(dbHandler.findGame("gid")).thenReturn(game);
        when(dbHandler.updateGameState("gid", GameState.GAME_COMPLETE)).thenReturn(GameState.GAME_COMPLETE);

        GameStatusMessage result = gameCoordinationWorker.endGame("gid").await().indefinitely();

        assertEquals(GameState.GAME_COMPLETE, result.gameState());
    }

    @Test
    void shouldReturnCompleteWhenEndGameAlreadyComplete() {
        GameInfo game = new GameInfo("gid", "cid", List.of(mock(Player.class), mock(Player.class)), GameState.GAME_COMPLETE, Instant.now());

        when(dbHandler.findGame("gid")).thenReturn(game);

        GameStatusMessage result = gameCoordinationWorker.endGame("gid").await().indefinitely();

        assertEquals(GameState.GAME_COMPLETE, result.gameState());
    }

    @Test
    void shouldGetGameHistory() {
        var p1 = mock(Player.class);
        when(p1.getPlayerName()).thenReturn("Alice");
        when(p1.getCurrentDraftPack()).thenReturn(3);
        when(p1.getCardPacks()).thenReturn(List.of(mock(CardPack.class)));
        when(p1.isReadyForMerge()).thenReturn(false);
        var p2 = mock(Player.class);
        when(p2.getPlayerName()).thenReturn("Bob");
        when(p2.getCurrentDraftPack()).thenReturn(5);
        when(p2.getCardPacks()).thenReturn(List.of(mock(CardPack.class), mock(CardPack.class)));
        when(p2.isReadyForMerge()).thenReturn(true);

        GameInfo game = new GameInfo("gid", "cid", List.of(p1, p2), GameState.GAME_STARTED, Instant.now());
        when(dbHandler.findGamesByAccountID("acct-1")).thenReturn(List.of(game));

        var result = gameCoordinationWorker.getGameHistory("acct-1").await().indefinitely();

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).player1Name());
        assertEquals("Bob", result.get(0).player2Name());
    }

    @Test
    void shouldGetGameHistoryWithEmptyPlayers() {
        GameInfo game = new GameInfo("gid", "cid", List.of(), GameState.GAME_STARTED, Instant.now());
        when(dbHandler.findGamesByAccountID("acct-1")).thenReturn(List.of(game));

        var result = gameCoordinationWorker.getGameHistory("acct-1").await().indefinitely();

        assertEquals(1, result.size());
        assertNull(result.get(0).player1Name());
        assertNull(result.get(0).player2Name());
        assertEquals(0, result.get(0).player1TotalPacks());
    }

    @Test
    void shouldAutoCompleteGameWhenMergedAndBothReadyDuringDraft() {
        var p1 = mock(Player.class);
        when(p1.isReadyForMerge()).thenReturn(true);
        when(p1.getAccountID()).thenReturn("acct-1");
        var p2 = mock(Player.class);
        when(p2.isReadyForMerge()).thenReturn(true);
        var card = mock(Card.class);
        when(card.getName()).thenReturn("Bolt");
        when(p1.draftCard("card-1", 0, false)).thenReturn(card);

        GameInfo game = new GameInfo("gid", "cid", List.of(p1, p2), GameState.GAME_MERGED, Instant.now());
        when(dbHandler.findGame("gid")).thenReturn(game);

        gameCoordinationWorker.draftCard("acct-1", 0, "card-1", false, "gid");
    }

    @Test
    void shouldAutoCompleteGameWhenBothReadyAfterMerge() {
        var p1 = mock(Player.class);
        when(p1.isReadyForMerge()).thenReturn(true);
        when(p1.getAccountID()).thenReturn("acct-1");
        var p2 = mock(Player.class);
        when(p2.isReadyForMerge()).thenReturn(true);
        var card = mock(Card.class);
        when(card.getName()).thenReturn("Bolt");
        when(p1.draftCard("card-1", 0, false)).thenReturn(card);

        GameInfo game = new GameInfo("gid", "cid", List.of(p1, p2), GameState.GAME_MERGED, Instant.now());
        when(dbHandler.findGame("gid")).thenReturn(game);

        gameCoordinationWorker.draftCard("acct-1", 0, "card-1", false, "gid");
    }
}
