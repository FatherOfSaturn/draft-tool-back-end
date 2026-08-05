package org.magic.classicDraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.classicDraft.api.ClassicGameCreationInfo;
import org.magic.classicDraft.api.ClassicGameInfo;
import org.magic.classicDraft.api.ClassicGameState;
import org.magic.classicDraft.api.ClassicGameSummary;
import org.magic.classicDraft.api.ClassicPlayerCreationInfo;
import org.magic.classicDraft.api.DraftDirection;
import org.magic.classicDraft.api.DraftPlayerSnapshot;
import org.magic.classicDraft.api.PlayerDraftCheck;
import org.magic.classicDraft.api.PlayerDraftData;
import org.magic.classicDraft.app.GameCoordination.ClassicGameCoordinationWorker;
import org.magic.pyramidDraft.api.GameStatusMessage;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardDetails;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class GameResourceTest {

    @Mock
    ClassicGameCoordinationWorker gameWorker;

    GameResource resource;

    private final CardDetails details = new CardDetails("set", "set_name", "scryfall_id", "img_small", "img_normal", null, "Lightning Bolt", "Instant", 1, List.of("R"));

    @BeforeEach
    void setup() {
        resource = new GameResource(gameWorker);
    }

    @Test
    void shouldCreateAndStartGame() {
        var info = new ClassicGameCreationInfo("cube-123",
                List.of(
                    new ClassicPlayerCreationInfo("P1", "acc1"),
                    new ClassicPlayerCreationInfo("P2", "acc2"),
                    new ClassicPlayerCreationInfo("P3", "acc3"),
                    new ClassicPlayerCreationInfo("P4", "acc4")),
                12, 4, 3);

        var expectedGame = new ClassicGameInfo("game-456", "cube-123", "classic",
                List.of(), ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(gameWorker.startGame(info)).thenReturn(Uni.createFrom().item(expectedGame));

        ClassicGameInfo result = resource.createAndStartGame(info).await().atMost(Duration.ofSeconds(3));

        assertNotNull(result);
        assertEquals("game-456", result.getGameID());
        assertEquals(ClassicGameState.GAME_STARTED, result.getGameState());
    }

    @Test
    void shouldDraftCard() {
        var card = new Card("Lightning Bolt", details, "card-789", 1, "Instant");

        when(gameWorker.draftCard("Alice", "card-789", "game-456")).thenReturn(card);

        Card result = resource.draftCard("Alice", "card-789", "game-456")
                .await().atMost(Duration.ofSeconds(3));

        assertNotNull(result);
        assertEquals("Lightning Bolt", result.getName());
    }

    @Test
    void shouldFetchGameData() {
        var gameInfo = new ClassicGameInfo("game-456", "cube-123", "classic",
                List.of(), ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);

        when(gameWorker.getGameInfo("game-456")).thenReturn(Uni.createFrom().item(gameInfo));

        ClassicGameInfo result = resource.getCurrentGameInfo("game-456").await().atMost(Duration.ofSeconds(3));

        assertNotNull(result);
        assertEquals("game-456", result.getGameID());
    }

    @Test
    void shouldGetDraftCheck() {
        var check = new PlayerDraftCheck(true, ClassicGameState.GAME_STARTED);

        when(gameWorker.getDraftCheck("game-456", "Alice")).thenReturn(Uni.createFrom().item(check));

        PlayerDraftCheck result = resource.getDraftCheck("game-456", "Alice")
                .await().atMost(Duration.ofSeconds(3));

        assertTrue(result.canDraft());
        assertEquals(ClassicGameState.GAME_STARTED, result.gameState());
    }

    @Test
    void shouldGetDraftData() {
        var snapshot = new DraftPlayerSnapshot("Alice", List.of(), List.of(), 0);
        var data = new PlayerDraftData("game-456", ClassicGameState.GAME_STARTED, DraftDirection.ASCENDING, snapshot);

        when(gameWorker.getDraftData("game-456", "Alice")).thenReturn(Uni.createFrom().item(data));

        PlayerDraftData result = resource.getDraftData("game-456", "Alice")
                .await().atMost(Duration.ofSeconds(3));

        assertEquals("game-456", result.gameID());
        assertEquals("Alice", result.player().playerName());
    }

    @Test
    void shouldEndGame() {
        when(gameWorker.endGame("game-456")).thenReturn(
                Uni.createFrom().item(new GameStatusMessage("game-456", org.magic.pyramidDraft.api.GameState.GAME_COMPLETE)));

        var result = resource.endGame("game-456").await().atMost(Duration.ofSeconds(3));

        assertEquals("game-456", result.gameID());
    }

    @Test
    void shouldGetGameHistory() {
        var summary = new ClassicGameSummary("game-456", "cube-123", ClassicGameState.GAME_COMPLETE,
                "Alice", 0, 5, 2, 2, Instant.now());

        when(gameWorker.getGameHistory("Alice")).thenReturn(Uni.createFrom().item(List.of(summary)));

        var result = resource.getGameHistory("Alice").await().atMost(Duration.ofSeconds(3));

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).playerName());
    }

    @Test
    void shouldHandleEmptyHistory() {
        when(gameWorker.getGameHistory("Unknown")).thenReturn(Uni.createFrom().item(List.of()));

        var result = resource.getGameHistory("Unknown").await().atMost(Duration.ofSeconds(3));

        assertTrue(result.isEmpty());
    }
}
