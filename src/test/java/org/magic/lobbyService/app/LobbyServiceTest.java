package org.magic.lobbyService.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.lobbyService.api.CreateLobbyRequest;
import org.magic.lobbyService.api.Lobby;
import org.magic.lobbyService.api.LobbyPlayer;
import org.magic.lobbyService.api.LobbyStatus;
import org.magic.lobbyService.api.StartGameRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;

@ExtendWith(MockitoExtension.class)
class LobbyServiceTest {

    @Mock
    LobbyDbHandler dbHandler;

    @Mock
    DraftTypeHandler pyramidHandler;

    @Mock
    DraftTypeHandler classicHandler;

    LobbyService service;

    @BeforeEach
    void setup() {
        lenient().when(pyramidHandler.draftType()).thenReturn("pyramid");
        lenient().when(pyramidHandler.defaultMinPlayers()).thenReturn(2);
        lenient().when(pyramidHandler.defaultMaxPlayers()).thenReturn(2);
        lenient().when(classicHandler.draftType()).thenReturn("classic");
        lenient().when(classicHandler.defaultMinPlayers()).thenReturn(4);
        lenient().when(classicHandler.defaultMaxPlayers()).thenReturn(12);
    }

    @Test
    void shouldRoutePyramidLobbyToPyramidHandler() {
        Lobby lobby = lobby("pyramid", 2, 2, 2);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        when(pyramidHandler.startGame(eq("cube-1"), anyList(), anyMap()))
                .thenReturn(Uni.createFrom().item("game-123"));

        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        Lobby result = service.startGame("ABC12", new StartGameRequest("host-1")).await().indefinitely();

        assertEquals("game-123", result.getGameID());
        assertEquals(LobbyStatus.STARTED, result.getStatus());
        verify(pyramidHandler).startGame(eq("cube-1"), anyList(), eq(lobby.getConfig()));
        verify(dbHandler, atLeast(2)).updateLobby(any());
    }

    @Test
    void shouldRouteClassicLobbyToClassicHandler() {
        Lobby lobby = lobby("classic", 4, 12, 4);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        when(classicHandler.startGame(eq("cube-1"), anyList(), anyMap()))
                .thenReturn(Uni.createFrom().item("classic-game-1"));

        service = new LobbyService(dbHandler, instanceOf(classicHandler));

        Lobby result = service.startGame("ABC12", new StartGameRequest("host-1")).await().indefinitely();

        assertEquals("classic-game-1", result.getGameID());
        assertEquals(LobbyStatus.STARTED, result.getStatus());
        verify(classicHandler).startGame(eq("cube-1"), anyList(), eq(lobby.getConfig()));
        assertEquals(Map.of("cubeID", "cube-1"), result.getConfig());
    }

    @Test
    void shouldPassPlayersInSlotOrderToHandler() {
        Lobby lobby = lobby("pyramid", 2, 2, 2);
        lobby.getPlayers().get(0).setSlotIndex(1);
        lobby.getPlayers().get(1).setSlotIndex(0);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        when(pyramidHandler.startGame(anyString(), anyList(), anyMap()))
                .thenReturn(Uni.createFrom().item("game-123"));

        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        service.startGame("ABC12", new StartGameRequest("host-1")).await().indefinitely();

        ArgumentCaptor<List<LobbyPlayer>> captor = ArgumentCaptor.forClass(List.class);
        verify(pyramidHandler).startGame(anyString(), captor.capture(), anyMap());
        List<LobbyPlayer> passed = captor.getValue();
        assertEquals(0, passed.get(0).getSlotIndex());
        assertEquals(1, passed.get(1).getSlotIndex());
    }

    @Test
    void shouldResetLobbyToWaitingWhenDraftTypeIsUnknown() {
        Lobby lobby = lobby("bogus", 4, 12, 4);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);

        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        assertThrows(IllegalStateException.class,
                () -> service.startGame("ABC12", new StartGameRequest("host-1")));

        assertEquals(LobbyStatus.WAITING, lobby.getStatus());
        verify(dbHandler, times(2)).updateLobby(lobby);
        verify(pyramidHandler, never()).startGame(anyString(), anyList(), anyMap());
    }

    @Test
    void shouldResetLobbyToWaitingWhenHandlerFails() {
        Lobby lobby = lobby("pyramid", 2, 2, 2);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        when(pyramidHandler.startGame(anyString(), anyList(), anyMap()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("boom")));

        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        assertThrows(RuntimeException.class,
                () -> service.startGame("ABC12", new StartGameRequest("host-1")).await().indefinitely());

        assertEquals(LobbyStatus.WAITING, lobby.getStatus());
    }

    @Test
    void shouldApplyPyramidDefaultsOnLobbyCreation() {
        when(dbHandler.lobbyExists(anyString())).thenReturn(false);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        Lobby result = service.createLobby(createLobbyRequest("pyramid", null, null)).await().indefinitely();

        assertEquals(2, result.getMinPlayers());
        assertEquals(2, result.getMaxPlayers());
    }

    @Test
    void shouldApplyClassicDefaultsOnLobbyCreation() {
        when(dbHandler.lobbyExists(anyString())).thenReturn(false);
        service = new LobbyService(dbHandler, instanceOf(classicHandler));

        Lobby result = service.createLobby(createLobbyRequest("classic", null, null)).await().indefinitely();

        assertEquals(4, result.getMinPlayers());
        assertEquals(12, result.getMaxPlayers());
    }

    @Test
    void shouldKeepExplicitPlayerOverridesOnLobbyCreation() {
        when(dbHandler.lobbyExists(anyString())).thenReturn(false);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        Lobby result = service.createLobby(createLobbyRequest("pyramid", 3, 5)).await().indefinitely();

        assertEquals(3, result.getMinPlayers());
        assertEquals(5, result.getMaxPlayers());
    }

    @SuppressWarnings("unchecked")
    private Instance<DraftTypeHandler> instanceOf(final DraftTypeHandler... handlers) {
        Instance<DraftTypeHandler> instance = mock(Instance.class);
        when(instance.stream()).thenReturn(List.of(handlers).stream());
        return instance;
    }

    private CreateLobbyRequest createLobbyRequest(final String draftType,
                                                  final Integer minPlayers,
                                                  final Integer maxPlayers) {
        return new CreateLobbyRequest(draftType, Map.of(), "host-1", "Host", minPlayers, maxPlayers);
    }

    private Lobby lobby(final String draftType, final int minPlayers, final int maxPlayers, final int playerCount) {
        List<LobbyPlayer> players = java.util.stream.IntStream.range(0, playerCount)
                .mapToObj(i -> new LobbyPlayer("acc-" + i, "P" + i, i, "tok-" + i, Instant.now()))
                .toList();
        return new Lobby(
                "ABC12",
                draftType,
                Map.of("cubeID", "cube-1"),
                "host-1",
                players,
                minPlayers,
                maxPlayers,
                null,
                LobbyStatus.WAITING,
                Instant.now(),
                null);
    }
}
