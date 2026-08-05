package org.magic.classicDraft.app.GameCoordination;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.classicDraft.api.ClassicGameCreationInfo;
import org.magic.classicDraft.api.ClassicGameInfo;
import org.magic.classicDraft.api.ClassicGameState;
import org.magic.classicDraft.api.ClassicGameSummary;
import org.magic.classicDraft.api.ClassicPlayer;
import org.magic.classicDraft.api.ClassicPlayerCreationInfo;
import org.magic.classicDraft.api.DraftDirection;
import org.magic.classicDraft.api.PlayerDraftCheck;
import org.magic.classicDraft.api.PlayerDraftData;
import org.magic.pyramidDraft.api.GameStatusMessage;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardDetails;
import org.magic.pyramidDraft.api.card.CardPack;
import org.magic.pyramidDraft.api.card.Cube;
import org.magic.pyramidDraft.app.CubeDownloader;
import org.magic.common.util.JsonUtility;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class ClassicGameCoordinationWorkerTest {

    @Mock
    ClassicDraftDbHandler dbHandler;
    @Mock
    CubeDownloader cubeDownloader;

    ClassicGameCoordinationWorker worker;
    private final CardDetails details = new CardDetails("set", "set_name", "scryfall_id", "img_small", "img_normal", null, "Lightning Bolt", "Instant", 1, List.of("R"));

    @BeforeEach
    void setup() {
        worker = new ClassicGameCoordinationWorker(dbHandler, cubeDownloader);
    }

    private CardPack createPack(int packNumber, int size) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            CardDetails d = new CardDetails("set", "set_name", "cid-" + packNumber + "-" + i,
                "img_small", "img_normal", null, "Card " + packNumber + "-" + i, "Creature", 1, List.of("1"));
            cards.add(new Card("Card " + packNumber + "-" + i, d, "cid-" + packNumber + "-" + i, 1, "Creature"));
        }
        return new CardPack(packNumber, cards, size, false);
    }

    private ClassicPlayer createPlayer(String name, int order, int packCount, int cardsPerPack) {
        List<CardPack> dealt = new ArrayList<>();
        for (int i = 0; i < packCount; i++) {
            int packNum = order * packCount + i;
            dealt.add(createPack(packNum, cardsPerPack));
        }
        return new ClassicPlayer(name, "acct-" + order, order, dealt, null, null);
    }

    @Test
    void testStartGame() throws IOException {
        ClassicGameCreationInfo info = new ClassicGameCreationInfo("cube-1",
                List.of(
                    new ClassicPlayerCreationInfo("Alice", "acct-1"),
                    new ClassicPlayerCreationInfo("Bob", "acct-2"),
                    new ClassicPlayerCreationInfo("Charlie", "acct-3"),
                    new ClassicPlayerCreationInfo("Diana", "acct-4")),
                12, 4, 3);

        Cube cube = createCubeFromJson("JoshCube.json");
        when(cubeDownloader.getCubeForCubeID("cube-1")).thenReturn(Uni.createFrom().item(cube));
        when(dbHandler.addGame(any())).thenReturn("game-1");

        ClassicGameInfo game = worker.startGame(info).await().atMost(Duration.ofSeconds(3));

        assertNotNull(game);
        assertNotNull(game.getGameID());
        assertEquals("classic", game.getGameType());
        assertEquals(ClassicGameState.GAME_STARTED, game.getGameState());
        assertEquals(0, game.getCurrentPackIndex());
        assertEquals(DraftDirection.ASCENDING, game.getDraftDirection());
        assertEquals(4, game.getPlayers().size());
        for (ClassicPlayer p : game.getPlayers()) {
            assertEquals(3, p.getDealtCardPacks().size());
            assertEquals(4, p.getDealtCardPacks().get(0).getCardsInPack().size());
            assertEquals(1, p.getActiveCardPacks().size(),
                    "Each player should have generation 0 dealt into activeCardPacks on game start");
            assertTrue(p.getActiveCardPacks().get(0).getCardsInPack().stream()
                    .anyMatch(c -> c.getCardID() != null));
        }
    }

    @Test
    void testDraftCard() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 6),
                createPlayer("Bob", 1, 2, 6),
                createPlayer("Charlie", 2, 2, 6),
                createPlayer("Diana", 3, 2, 6));

        for (ClassicPlayer p : players) {
            p.getActiveCardPacks().add(p.getDealtCardPacks().get(0));
        }

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);
        when(dbHandler.updateGame(any())).thenAnswer(inv -> inv.getArgument(0));

        String firstCardId = players.get(0).getActiveCardPacks().get(0).getCardsInPack().get(0).getCardID();
        Card drafted = worker.draftCard("Alice", firstCardId, "game-1");

        assertNotNull(drafted);
        assertEquals(1, players.get(0).getCardsDrafted().size());
        assertTrue(players.get(0).getActiveCardPacks().isEmpty(),
                "After drafting, Alice's activeCardPacks should be empty");
        assertFalse(players.get(1).getActiveCardPacks().isEmpty(),
                "The pack should have been passed to Bob");
    }

    @Test
    void testDraftCardRejectsNonexistentPlayer() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 6),
                createPlayer("Bob", 1, 2, 6));

        for (ClassicPlayer p : players) {
            p.getActiveCardPacks().add(p.getDealtCardPacks().get(0));
        }

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);

        assertThrows(IllegalArgumentException.class, () -> {
            worker.draftCard("Eve", "cid-0-0", "game-1");
        });
    }

    @Test
    void testDraftCardRejectsCompletedGame() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 6),
                createPlayer("Bob", 1, 2, 6));

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_COMPLETE, Instant.now(), 2, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);

        assertThrows(IllegalStateException.class, () -> {
            worker.draftCard("Alice", "cid-0-0", "game-1");
        });
    }

    @Test
    void testDraftCardRejectsWithNoActivePack() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 6),
                createPlayer("Bob", 1, 2, 6));

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);

        assertThrows(IllegalStateException.class, () -> {
            worker.draftCard("Alice", "cid-0-0", "game-1");
        });
    }

    @Test
    void testAdvanceGenerationWhenAllPacksExhausted() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 1),
                createPlayer("Bob", 1, 2, 1));

        for (ClassicPlayer p : players) {
            p.getActiveCardPacks().add(p.getDealtCardPacks().get(0));
        }

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);
        when(dbHandler.updateGame(any())).thenAnswer(inv -> inv.getArgument(0));

        String aliceCard = players.get(0).getActiveCardPacks().get(0).getCardsInPack().get(0).getCardID();
        String bobCard = players.get(1).getActiveCardPacks().get(0).getCardsInPack().get(0).getCardID();

        worker.draftCard("Alice", aliceCard, "game-1");
        worker.draftCard("Bob", bobCard, "game-1");

        assertEquals(1, players.get(0).getActiveCardPacks().size(),
                "Alice should have generation 1 pack after advancing");
        assertEquals(1, players.get(1).getActiveCardPacks().size(),
                "Bob should have generation 1 pack after advancing");
        assertEquals(1, game.getCurrentPackIndex());
        assertEquals(DraftDirection.DESCENDING, game.getDraftDirection());
    }

    @Test
    void testCompleteGameWhenAllPacksExhausted() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 1, 1),
                createPlayer("Bob", 1, 1, 1));

        for (ClassicPlayer p : players) {
            p.getActiveCardPacks().add(p.getDealtCardPacks().get(0));
        }

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);
        when(dbHandler.updateGame(any())).thenAnswer(inv -> inv.getArgument(0));

        String aliceCard = players.get(0).getActiveCardPacks().get(0).getCardsInPack().get(0).getCardID();
        String bobCard = players.get(1).getActiveCardPacks().get(0).getCardsInPack().get(0).getCardID();

        worker.draftCard("Alice", aliceCard, "game-1");

        when(dbHandler.findGame("game-1")).thenReturn(game);
        worker.draftCard("Bob", bobCard, "game-1");

        assertEquals(ClassicGameState.GAME_COMPLETE, game.getGameState());
    }

    @Test
    void testPackPassesToNextPlayerInAscendingDirection() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 1, 3),
                createPlayer("Bob", 1, 1, 3));

        for (ClassicPlayer p : players) {
            p.getActiveCardPacks().add(p.getDealtCardPacks().get(0));
        }

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);
        when(dbHandler.updateGame(any())).thenAnswer(inv -> inv.getArgument(0));

        String aliceCard = players.get(0).getActiveCardPacks().get(0).getCardsInPack().get(0).getCardID();
        worker.draftCard("Alice", aliceCard, "game-1");

        assertEquals(0, players.get(0).getActiveCardPacks().size(),
                "Alice should have no active pack after drafting");
        assertEquals(2, players.get(1).getActiveCardPacks().size(),
                "Bob should have his own pack + Alice's passed pack");
    }

    @Test
    void testEndGame() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 1, 6),
                createPlayer("Bob", 1, 1, 6));

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);
        when(dbHandler.updateGameState("game-1", ClassicGameState.GAME_COMPLETE)).thenReturn(ClassicGameState.GAME_COMPLETE);

        GameStatusMessage result = worker.endGame("game-1").await().indefinitely();

        assertNotNull(result);
    }

    @Test
    void testEndGameWhenAlreadyComplete() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 1, 6),
                createPlayer("Bob", 1, 1, 6));

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_COMPLETE, Instant.now(), 1, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);

        GameStatusMessage result = worker.endGame("game-1").await().indefinitely();

        assertNotNull(result);
    }

    @Test
    void testGetGameInfo() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 6),
                createPlayer("Bob", 1, 2, 6));

        ClassicGameInfo expected = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_COMPLETE, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(expected);

        ClassicGameInfo result = worker.getGameInfo("game-1").await().indefinitely();

        assertEquals(expected, result);
    }

    @Test
    void testGetGameInfoThrowsConflictWhileGameInProgress() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 6),
                createPlayer("Bob", 1, 2, 6));

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> worker.getGameInfo("game-1").await().indefinitely());

        assertEquals(Response.Status.CONFLICT.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testGetGameInfoThrowsNotFoundWhenMissing() {
        when(dbHandler.findGame("missing-game")).thenThrow(new jakarta.ws.rs.NotFoundException());

        assertThrows(jakarta.ws.rs.NotFoundException.class,
                () -> worker.getGameInfo("missing-game").await().indefinitely());
    }

    @Test
    void testGetGameHistory() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 6),
                createPlayer("Bob", 1, 2, 6));

        for (ClassicPlayer p : players) {
            p.getActiveCardPacks().add(p.getDealtCardPacks().get(0));
        }

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_COMPLETE, Instant.now(), 2, DraftDirection.ASCENDING);

        when(dbHandler.findGamesByPlayerName("Alice")).thenReturn(List.of(game));

        var result = worker.getGameHistory("Alice").await().indefinitely();

        assertEquals(1, result.size());
        ClassicGameSummary summary = result.get(0);
        assertEquals("game-1", summary.gameID());
        assertEquals("Alice", summary.playerName());
        assertEquals(0, summary.draftOrderNumber());
        assertEquals(0, summary.cardsDraftedCount());
    }

    @Test
    void testGetGameHistoryWithNoGames() {
        when(dbHandler.findGamesByPlayerName("Unknown")).thenReturn(List.of());

        var result = worker.getGameHistory("Unknown").await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDraftCheckReturnsCanDraftTrue() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 6),
                createPlayer("Bob", 1, 2, 6));

        for (ClassicPlayer p : players) {
            p.getActiveCardPacks().add(p.getDealtCardPacks().get(0));
        }

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findDraftCheck("game-1", "Alice"))
                .thenReturn(new org.bson.Document("gameState", "GAME_STARTED").append("canDraft", true));

        PlayerDraftCheck result = worker.getDraftCheck("game-1", "Alice").await().indefinitely();

        assertTrue(result.canDraft());
        assertEquals(ClassicGameState.GAME_STARTED, result.gameState());
    }

    @Test
    void testGetDraftCheckReturnsCanDraftFalse() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 6),
                createPlayer("Bob", 1, 2, 6));

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findDraftCheck("game-1", "Alice"))
                .thenReturn(new org.bson.Document("gameState", "GAME_STARTED").append("canDraft", false));

        PlayerDraftCheck result = worker.getDraftCheck("game-1", "Alice").await().indefinitely();

        assertFalse(result.canDraft());
        assertEquals(ClassicGameState.GAME_STARTED, result.gameState());
    }

    @Test
    void testGetDraftData() {
        List<ClassicPlayer> players = List.of(
                createPlayer("Alice", 0, 2, 6),
                createPlayer("Bob", 1, 2, 6));

        for (ClassicPlayer p : players) {
            p.getActiveCardPacks().add(p.getDealtCardPacks().get(0));
        }

        ClassicGameInfo game = new ClassicGameInfo("game-1", "cube-1", "classic",
                players, ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(dbHandler.findGame("game-1")).thenReturn(game);

        PlayerDraftData result = worker.getDraftData("game-1", "Alice").await().indefinitely();

        assertEquals("game-1", result.gameID());
        assertEquals(ClassicGameState.GAME_STARTED, result.gameState());
        assertEquals(DraftDirection.ASCENDING, result.draftDirection());
        assertNotNull(result.player());
        assertEquals("Alice", result.player().playerName());
        assertEquals(1, result.player().activeCardPacks().size());
        assertTrue(result.player().cardsDrafted().isEmpty());
        assertEquals(12, result.player().cardsLeftToDraft());
    }

    @Test
    void testGetDraftDataThrowsNotFoundWhenMissing() {
        when(dbHandler.findGame("missing-game")).thenThrow(new jakarta.ws.rs.NotFoundException());

        assertThrows(jakarta.ws.rs.NotFoundException.class,
                () -> worker.getDraftData("missing-game", "Alice").await().indefinitely());
    }

    protected Cube createCubeFromJson(final String fileName) throws IOException {
        InputStream cubeIS = getClass().getClassLoader().getResourceAsStream(fileName);
        String cubeString = IOUtils.toString(cubeIS, "UTF-8");
        return JsonUtility.getInstance().fromJson(cubeString, Cube.class);
    }
}
