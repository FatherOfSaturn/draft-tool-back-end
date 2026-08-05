package org.magic.pyramidDraft.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.lobbyService.api.LobbyPlayer;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.GameInfo;
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
    void shouldGenerateGameIdOnBackendAndMapAnonymousPlayers() {
        GameInfo gameInfo = mock(GameInfo.class);
        when(gameInfo.getGameID()).thenReturn("game-123");
        when(gameWorker.startGame(any())).thenReturn(Uni.createFrom().item(gameInfo));

        PyramidDraftTypeHandler handler = new PyramidDraftTypeHandler(gameWorker);

        List<LobbyPlayer> players = List.of(
                new LobbyPlayer("acc-1", "P1", 0, "tok-1", null),
                new LobbyPlayer(null, "P2", 1, "tok-2", null));

        String result = handler.startGame("cube-1", players,
                Map.of("numberOfDoubleDraftPicksPerPlayer", 3)).await().atMost(Duration.ofSeconds(3));

        assertEquals("game-123", result);

        ArgumentCaptor<GameCreationInfo> captor = ArgumentCaptor.forClass(GameCreationInfo.class);
        verify(gameWorker).startGame(captor.capture());

        GameCreationInfo info = captor.getValue();
        assertNotNull(info.gameID());
        assertFalse(info.gameID().isBlank());
        assertEquals("cube-1", info.cubeID());
        assertEquals(3, info.numberOfDoubleDraftPicksPerPlayer());
        assertEquals(2, info.playerInfo().size());
        assertEquals("acc-1", info.playerInfo().get(0).accountID());
        assertEquals("guest-tok-2", info.playerInfo().get(1).accountID());
    }

    @Test
    void shouldDefaultDoubleDraftPicksToZeroWhenConfigMissing() {
        GameInfo gameInfo = mock(GameInfo.class);
        when(gameInfo.getGameID()).thenReturn("game-123");
        when(gameWorker.startGame(any())).thenReturn(Uni.createFrom().item(gameInfo));

        PyramidDraftTypeHandler handler = new PyramidDraftTypeHandler(gameWorker);

        handler.startGame("cube-1", List.of(new LobbyPlayer("acc-1", "P1", 0, "tok-1", null)),
                Map.of()).await().indefinitely();

        ArgumentCaptor<GameCreationInfo> captor = ArgumentCaptor.forClass(GameCreationInfo.class);
        verify(gameWorker).startGame(captor.capture());
        assertEquals(0, captor.getValue().numberOfDoubleDraftPicksPerPlayer());
    }
}
