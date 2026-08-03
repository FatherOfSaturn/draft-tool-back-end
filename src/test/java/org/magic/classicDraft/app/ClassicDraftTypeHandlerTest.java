package org.magic.classicDraft.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.classicDraft.api.ClassicGameCreationInfo;
import org.magic.classicDraft.api.ClassicGameInfo;
import org.magic.classicDraft.api.ClassicGameState;
import org.magic.classicDraft.api.DraftDirection;
import org.magic.classicDraft.app.GameCoordination.ClassicGameCoordinationWorker;
import org.magic.lobbyService.api.LobbyPlayer;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class ClassicDraftTypeHandlerTest {

    @Mock
    ClassicGameCoordinationWorker gameWorker;

    @Test
    void shouldStartClassicGameAndReturnGeneratedGameId() {
        ClassicDraftTypeHandler handler = new ClassicDraftTypeHandler(gameWorker);

        ClassicGameInfo created = new ClassicGameInfo("generated-id", "cube-1", "classic",
                List.of(), ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);
        when(gameWorker.startGame(any())).thenReturn(Uni.createFrom().item(created));

        List<LobbyPlayer> players = List.of(
                new LobbyPlayer("acc-1", "P1", 0, "tok-1", Instant.now()),
                new LobbyPlayer("acc-2", "P2", 1, "tok-2", Instant.now()),
                new LobbyPlayer("acc-3", "P3", 2, "tok-3", Instant.now()),
                new LobbyPlayer("acc-4", "P4", 3, "tok-4", Instant.now()));

        String result = handler.startGame("cube-1", players, Map.of("cardsPerPack", 15, "packsPerPlayer", 2))
                .await().atMost(Duration.ofSeconds(3));

        assertEquals("generated-id", result);

        ArgumentCaptor<ClassicGameCreationInfo> captor = ArgumentCaptor.forClass(ClassicGameCreationInfo.class);
        verify(gameWorker).startGame(captor.capture());
        ClassicGameCreationInfo info = captor.getValue();
        assertEquals("cube-1", info.cubeID());
        assertEquals(15, info.cardsPerPack());
        assertEquals(2, info.packsPerPlayer());
        assertEquals(8, info.numberOfPacks());
        assertEquals(4, info.players().size());
    }

    @Test
    void shouldApplyDefaultsAndSortPlayersBySlot() {
        ClassicDraftTypeHandler handler = new ClassicDraftTypeHandler(gameWorker);

        ClassicGameInfo created = new ClassicGameInfo("generated-id", "cube-1", "classic",
                List.of(), ClassicGameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING);
        when(gameWorker.startGame(any())).thenReturn(Uni.createFrom().item(created));

        List<LobbyPlayer> players = List.of(
                new LobbyPlayer("acc-2", "P2", 1, "tok-2", Instant.now()),
                new LobbyPlayer("acc-1", "P1", 0, "tok-1", Instant.now()),
                new LobbyPlayer("acc-3", "P3", 2, "tok-3", Instant.now()),
                new LobbyPlayer("acc-4", "P4", 3, "tok-4", Instant.now()));

        handler.startGame("cube-1", players, Map.of()).await().atMost(Duration.ofSeconds(3));

        ArgumentCaptor<ClassicGameCreationInfo> captor = ArgumentCaptor.forClass(ClassicGameCreationInfo.class);
        verify(gameWorker).startGame(captor.capture());
        ClassicGameCreationInfo info = captor.getValue();
        assertEquals(15, info.cardsPerPack());
        assertEquals(3, info.packsPerPlayer());
        assertEquals(12, info.numberOfPacks());
        assertEquals("P1", info.players().get(0).playerName());
        assertEquals("acc-2", info.players().get(1).accountID());
    }
}
