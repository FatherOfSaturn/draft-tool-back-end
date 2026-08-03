package org.magic.pyramidDraft.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.lobbyService.api.LobbyPlayer;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.app.GameCoordination.GameCoordinationWorker;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class PyramidDraftTypeHandlerTest {

    @Mock
    GameCoordinationWorker gameWorker;

    @Test
    void shouldStartGameAndReturnGeneratedGameId() {
        PyramidDraftTypeHandler handler = new PyramidDraftTypeHandler(gameWorker);

        GameInfo created = new GameInfo("generated-id", "cube-1", List.of(), GameState.GAME_STARTED, Instant.now());
        when(gameWorker.startGame(any())).thenReturn(Uni.createFrom().item(created));

        List<LobbyPlayer> players = List.of(
                new LobbyPlayer("acc-1", "P1", 0, "tok-1", Instant.now()),
                new LobbyPlayer("acc-2", "P2", 1, "tok-2", Instant.now()));

        String result = handler.startGame("cube-1", players, Map.of("numberOfDoubleDraftPicksPerPlayer", 4))
                .await().atMost(Duration.ofSeconds(3));

        assertEquals("generated-id", result);

        ArgumentCaptor<GameCreationInfo> captor = ArgumentCaptor.forClass(GameCreationInfo.class);
        verify(gameWorker).startGame(captor.capture());
        GameCreationInfo info = captor.getValue();
        assertEquals("cube-1", info.cubeID());
        assertEquals(4, info.numberOfDoubleDraftPicksPerPlayer());
        assertEquals(2, info.playerInfo().size());
        assertEquals("acc-1", info.playerInfo().get(0).accountID());
    }

    @Test
    void shouldMapAnonymousPlayersToGuestId() {
        PyramidDraftTypeHandler handler = new PyramidDraftTypeHandler(gameWorker);

        GameInfo created = new GameInfo("generated-id", "cube-1", List.of(), GameState.GAME_STARTED, Instant.now());
        when(gameWorker.startGame(any())).thenReturn(Uni.createFrom().item(created));

        List<LobbyPlayer> players = List.of(
                new LobbyPlayer(null, "Guest", 0, "tok-9", Instant.now()),
                new LobbyPlayer("acc-2", "P2", 1, "tok-2", Instant.now()));

        handler.startGame("cube-1", players, Map.of()).await().atMost(Duration.ofSeconds(3));

        ArgumentCaptor<GameCreationInfo> captor = ArgumentCaptor.forClass(GameCreationInfo.class);
        verify(gameWorker).startGame(captor.capture());
        GameCreationInfo info = captor.getValue();
        assertEquals("guest-tok-9", info.playerInfo().get(0).accountID());
        assertEquals(0, info.numberOfDoubleDraftPicksPerPlayer());
    }
}
