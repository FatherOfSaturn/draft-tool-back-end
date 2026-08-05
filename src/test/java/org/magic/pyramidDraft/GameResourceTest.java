package org.magic.pyramidDraft;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.api.GameStatusMessage;
import org.magic.pyramidDraft.api.GameSummary;
import org.magic.pyramidDraft.api.PlayerCreationInfo;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardDetails;
import org.magic.pyramidDraft.app.GameCoordination.GameCoordinationWorker;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
class GameResourceTest {

    @Mock
    GameCoordinationWorker gameWorker;

    GameResource resource;

    private final CardDetails details = new CardDetails("set", "set_name", "scryfall_id", "img_small", "img_normal", null, "Lightning Bolt", "Instant", 1, List.of("R"));

    @BeforeEach
    void setup() {
        resource = new GameResource(gameWorker);
    }

    @Test
    void shouldCreateAndStartGame() {
        var info = new GameCreationInfo("gid", "cube-123", List.of(new PlayerCreationInfo("P1", "acc1"), new PlayerCreationInfo("P2", "acc2")), 3);
        var expectedGame = new GameInfo("game-456", "cube-123", "pyramid", List.of(), GameState.GAME_STARTED, Instant.now());

        when(gameWorker.startGame(info)).thenReturn(Uni.createFrom().item(expectedGame));

        GameInfo result = resource.createAndStartGame(info).await().atMost(Duration.ofSeconds(3));

        assertNotNull(result);
        assertEquals("game-456", result.getGameID());
        assertEquals(GameState.GAME_STARTED, result.getGameState());
    }

    @Test
    void shouldDraftCard() {
        var card = new Card("Lightning Bolt", details, "card-789", 1, "Instant");

        when(gameWorker.draftCard("player-1", 2, "card-789", false, "game-456")).thenReturn(card);

        Card result = resource.draftCard("player-1", 2, "card-789", "game-456", false).await().atMost(Duration.ofSeconds(3));

        assertNotNull(result);
        assertEquals("Lightning Bolt", result.getName());
    }

    @Test
    void shouldFetchGameData() {
        var gameInfo = new GameInfo("game-456", "cube-123", "pyramid", List.of(), GameState.GAME_STARTED, Instant.now());

        when(gameWorker.getGameInfo("game-456")).thenReturn(Uni.createFrom().item(gameInfo));

        GameInfo result = resource.getCurrentPlayerInfo("game-456").await().atMost(Duration.ofSeconds(3));

        assertNotNull(result);
        assertEquals("game-456", result.getGameID());
    }

    @Test
    void shouldTriggerMergeAndSwap() {
        var statusMsg = new GameStatusMessage("game-456", GameState.GAME_MERGED);

        when(gameWorker.mergeAndSwapPacks("game-456")).thenReturn(Uni.createFrom().item(statusMsg));

        GameStatusMessage result = resource.triggerPackMergeAndSwap("game-456").await().atMost(Duration.ofSeconds(3));

        assertNotNull(result);
        assertEquals(GameState.GAME_MERGED, result.gameState());
    }

    @Test
    void shouldEndGame() {
        var statusMsg = new GameStatusMessage("game-456", GameState.GAME_COMPLETE);

        when(gameWorker.endGame("game-456")).thenReturn(Uni.createFrom().item(statusMsg));

        GameStatusMessage result = resource.endGame("game-456").await().atMost(Duration.ofSeconds(3));

        assertNotNull(result);
        assertEquals(GameState.GAME_COMPLETE, result.gameState());
    }

    @Test
    void shouldGetGameHistory() {
        var summaries = List.of(new GameSummary("g1", "cube-1", GameState.GAME_COMPLETE, "P1", 0, 0, false, "P2", 0, 0, false, Instant.now()));

        when(gameWorker.getGameHistory("player-1")).thenReturn(Uni.createFrom().item(summaries));

        List<GameSummary> result = resource.getGameHistory("player-1").await().atMost(Duration.ofSeconds(3));

        assertEquals(1, result.size());
        assertEquals("g1", result.get(0).gameID());
    }

    @Test
    void shouldDeleteGame() {
        when(gameWorker.deleteGame("game-456")).thenReturn(Uni.createFrom().item(Response.noContent().build()));

        Response result = resource.deleteGame("game-456").await().atMost(Duration.ofSeconds(3));

        assertEquals(204, result.getStatus());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingGame() {
        when(gameWorker.deleteGame("missing")).thenReturn(Uni.createFrom().item(Response.status(Response.Status.NOT_FOUND).build()));

        Response result = resource.deleteGame("missing").await().atMost(Duration.ofSeconds(3));

        assertEquals(404, result.getStatus());
    }
}
