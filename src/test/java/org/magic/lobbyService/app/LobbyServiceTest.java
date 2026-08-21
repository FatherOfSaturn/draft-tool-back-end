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
import org.magic.lobbyService.api.JoinLobbyRequest;
import org.magic.lobbyService.api.JoinLobbyResponse;
import org.magic.lobbyService.api.KickPlayerRequest;
import org.magic.lobbyService.api.LeaveLobbyRequest;
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

        Lobby result = service.startGame("ABC12", new StartGameRequest("tok-0")).await().indefinitely();

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

        Lobby result = service.startGame("ABC12", new StartGameRequest("tok-0")).await().indefinitely();

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

        service.startGame("ABC12", new StartGameRequest("tok-0")).await().indefinitely();

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
                () -> service.startGame("ABC12", new StartGameRequest("tok-0")).await().indefinitely());

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

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.startGame("ABC12", new StartGameRequest("tok-0")).await().indefinitely());

        assertEquals("Game creation failed: boom", ex.getMessage());
        assertEquals(LobbyStatus.WAITING, lobby.getStatus());
    }

    @Test
    void shouldApplyPyramidDefaultsOnLobbyCreation() {
        when(dbHandler.lobbyExists(anyString())).thenReturn(false);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        JoinLobbyResponse response = service.createLobby(createLobbyRequest("pyramid", null, null)).await().indefinitely();

        assertEquals(2, response.lobby().getMinPlayers());
        assertEquals(2, response.lobby().getMaxPlayers());
        assertEquals("host-1", response.lobby().getHostAccountID());
        assertNotNull(response.playerToken());
        assertEquals(response.playerToken(), response.lobby().getPlayers().get(0).getPlayerToken());
    }

    @Test
    void shouldApplyClassicDefaultsOnLobbyCreation() {
        when(dbHandler.lobbyExists(anyString())).thenReturn(false);
        service = new LobbyService(dbHandler, instanceOf(classicHandler));

        JoinLobbyResponse response = service.createLobby(createLobbyRequest("classic", null, null)).await().indefinitely();

        assertEquals(4, response.lobby().getMinPlayers());
        assertEquals(12, response.lobby().getMaxPlayers());
    }

    @Test
    void shouldKeepExplicitPlayerOverridesOnLobbyCreation() {
        when(dbHandler.lobbyExists(anyString())).thenReturn(false);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        JoinLobbyResponse response = service.createLobby(createLobbyRequest("pyramid", 3, 5)).await().indefinitely();

        assertEquals(3, response.lobby().getMinPlayers());
        assertEquals(5, response.lobby().getMaxPlayers());
    }

    @Test
    void shouldRejectStartWhenCallerIsNotHost() {
        Lobby lobby = lobby("pyramid", 2, 2, 2);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.startGame("ABC12", new StartGameRequest("some-other-token")).await().indefinitely());

        assertEquals("Only the host can start the game", ex.getMessage());
        verify(dbHandler, never()).updateLobby(any());
    }

    @Test
    void shouldRejectJoinWhenAccountAlreadyInLobby() {
        Lobby lobby = lobby("pyramid", 2, 2, 2);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.joinLobby("ABC12", new JoinLobbyRequest("acc-0", "New Name")).await().indefinitely());

        assertEquals("Already in lobby", ex.getMessage());
    }

    @Test
    void shouldRejectJoinWhenDisplayNameAlreadyTakenCaseInsensitive() {
        Lobby lobby = lobby("pyramid", 2, 2, 1);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.joinLobby("ABC12", new JoinLobbyRequest("acc-9", "  p0  ")).await().indefinitely());

        assertEquals("That name is already taken in this lobby", ex.getMessage());
    }

    @Test
    void shouldRejectJoinWhenLobbyIsFull() {
        Lobby lobby = lobby("pyramid", 2, 2, 2);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.joinLobby("ABC12", new JoinLobbyRequest("acc-9", "New Name")).await().indefinitely());

        assertEquals("Lobby is full", ex.getMessage());
    }

    @Test
    void shouldRejectJoinWhenDisplayNameIsBlank() {
        Lobby lobby = lobby("pyramid", 2, 2, 1);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.joinLobby("ABC12", new JoinLobbyRequest(null, "   ")).await().indefinitely());

        assertEquals("displayName cannot be blank", ex.getMessage());
    }

    @Test
    void shouldJoinLobbyWithTrimmedName() {
        Lobby lobby = lobby("pyramid", 2, 2, 1);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        JoinLobbyResponse response = service.joinLobby("ABC12", new JoinLobbyRequest("acc-9", "  New Player  "))
                .await().indefinitely();

        assertEquals(2, response.lobby().getPlayers().size());
        LobbyPlayer joined = response.lobby().getPlayers().get(1);
        assertEquals("New Player", joined.getDisplayName());
        assertEquals(response.playerToken(), joined.getPlayerToken());
        assertNull(response.lobby().getHostAloneSince());
        verify(dbHandler).updateLobby(lobby);
    }

    @Test
    void shouldRejectKickWhenNotHost() {
        Lobby lobby = lobbyWithHost("host-1", new LobbyPlayer(null, "Guest", 1, "tok-guest", Instant.now()));
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.kickPlayer("ABC12", new KickPlayerRequest("someone-else", 1)).await().indefinitely());

        assertEquals("Only the host can kick players", ex.getMessage());
        verify(dbHandler, never()).updateLobby(any());
    }

    @Test
    void shouldRejectKickWhenTargetSlotNotFound() {
        Lobby lobby = lobbyWithHost("host-1", new LobbyPlayer(null, "Guest", 1, "tok-guest", Instant.now()));
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.kickPlayer("ABC12", new KickPlayerRequest("tok-host", 99)).await().indefinitely());

        assertTrue(ex.getMessage().contains("not found in lobby ABC12"));
    }

    @Test
    void shouldRejectKickWhenTargetIsHost() {
        Lobby lobby = lobbyWithHost("host-1", new LobbyPlayer(null, "Guest", 1, "tok-guest", Instant.now()));
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.kickPlayer("ABC12", new KickPlayerRequest("tok-host", 0)).await().indefinitely());

        assertEquals("The host cannot be kicked from the lobby", ex.getMessage());
    }

    @Test
    void shouldRejectKickWhenLobbyNotWaiting() {
        Lobby lobby = lobbyWithHost("host-1", new LobbyPlayer(null, "Guest", 1, "tok-guest", Instant.now()));
        lobby.setStatus(LobbyStatus.STARTED);
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.kickPlayer("ABC12", new KickPlayerRequest("tok-host", 1)).await().indefinitely());

        assertTrue(ex.getMessage().contains("in status STARTED"));
    }

    @Test
    void shouldKickPlayerAndStartHostAloneClockWhenOnlyHostRemains() {
        Lobby lobby = lobbyWithHost("host-1", new LobbyPlayer(null, "Guest", 1, "tok-guest", Instant.now()));
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        Lobby result = service.kickPlayer("ABC12", new KickPlayerRequest("tok-host", 1)).await().indefinitely();

        assertEquals(1, result.getPlayers().size());
        assertEquals("host-1", result.getPlayers().get(0).getAccountID());
        assertNotNull(result.getHostAloneSince());
        verify(dbHandler).updateLobby(lobby);
    }

    @Test
    void shouldKickPlayerWithoutStartingHostAloneClockWhenOthersRemain() {
        Lobby lobby = lobbyWithHost("host-1",
                new LobbyPlayer(null, "Guest", 1, "tok-guest-1", Instant.now()),
                new LobbyPlayer(null, "Guest 2", 2, "tok-guest-2", Instant.now()));
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        Lobby result = service.kickPlayer("ABC12", new KickPlayerRequest("tok-host", 1)).await().indefinitely();

        assertEquals(2, result.getPlayers().size());
        assertNull(result.getHostAloneSince());
    }

    @Test
    void shouldLeaveLobbyByToken() {
        Lobby lobby = lobbyWithHost("host-1", new LobbyPlayer(null, "Guest", 1, "tok-guest", Instant.now()));
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        Lobby result = service.leaveLobby("ABC12", new LeaveLobbyRequest("tok-guest")).await().indefinitely();

        assertEquals(1, result.getPlayers().size());
        assertEquals("host-1", result.getPlayers().get(0).getAccountID());
        verify(dbHandler).updateLobby(lobby);
    }

    @Test
    void shouldRejectLeaveWhenTokenDoesNotMatchAnyPlayer() {
        Lobby lobby = lobbyWithHost("host-1", new LobbyPlayer(null, "Guest", 1, "tok-guest", Instant.now()));
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.leaveLobby("ABC12", new LeaveLobbyRequest("some-other-token")).await().indefinitely());

        assertTrue(ex.getMessage().contains("No player in lobby ABC12 matches the provided token"));
        verify(dbHandler, never()).updateLobby(any());
    }

    @Test
    void shouldRejectLeaveWithoutToken() {
        Lobby lobby = lobbyWithHost("host-1", new LobbyPlayer(null, "Guest", 1, "tok-guest", Instant.now()));
        when(dbHandler.findByCode("ABC12")).thenReturn(lobby);
        service = new LobbyService(dbHandler, instanceOf(pyramidHandler));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.leaveLobby("ABC12", new LeaveLobbyRequest(null)).await().indefinitely());

        assertEquals("playerToken is required to leave the lobby", ex.getMessage());
        verify(dbHandler, never()).updateLobby(any());
    }

    private Lobby lobbyWithHost(final String hostAccountID, final LobbyPlayer... others) {
        List<LobbyPlayer> players = new java.util.ArrayList<>();
        players.add(new LobbyPlayer(hostAccountID, "Host", 0, "tok-host", Instant.now()));
        players.addAll(java.util.List.of(others));
        return new Lobby(
                "ABC12",
                "pyramid",
                Map.of("cubeID", "cube-1"),
                hostAccountID,
                players,
                2,
                2,
                null,
                LobbyStatus.WAITING,
                Instant.now(),
                null);
    }

    @SuppressWarnings("unchecked")
    private Instance<DraftTypeHandler> instanceOf(final DraftTypeHandler... handlers) {
        Instance<DraftTypeHandler> instance = mock(Instance.class);
        lenient().when(instance.stream()).thenReturn(List.of(handlers).stream());
        return instance;
    }

    private CreateLobbyRequest createLobbyRequest(final String draftType,
                                                  final Integer minPlayers,
                                                  final Integer maxPlayers) {
        return new CreateLobbyRequest(draftType, Map.of(), "host-1", "Host", minPlayers, maxPlayers);
    }

    private Lobby lobby(final String draftType, final int minPlayers, final int maxPlayers, final int playerCount) {
        List<LobbyPlayer> players = new java.util.ArrayList<>(java.util.stream.IntStream.range(0, playerCount)
                .mapToObj(i -> new LobbyPlayer("acc-" + i, "P" + i, i, "tok-" + i, Instant.now()))
                .toList());
        return new Lobby(
                "ABC12",
                draftType,
                Map.of("cubeID", "cube-1"),
                "acc-0",
                players,
                minPlayers,
                maxPlayers,
                null,
                LobbyStatus.WAITING,
                Instant.now(),
                null);
    }
}
