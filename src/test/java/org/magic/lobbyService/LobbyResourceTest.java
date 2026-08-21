package org.magic.lobbyService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.lobbyService.api.JoinLobbyRequest;
import org.magic.lobbyService.api.JoinLobbyResponse;
import org.magic.lobbyService.api.KickPlayerRequest;
import org.magic.lobbyService.api.LeaveLobbyRequest;
import org.magic.lobbyService.api.Lobby;
import org.magic.lobbyService.api.LobbyPlayer;
import org.magic.lobbyService.api.LobbyStatus;
import org.magic.lobbyService.api.StartGameRequest;
import org.magic.lobbyService.app.LobbyService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.WebApplicationException;

@ExtendWith(MockitoExtension.class)
class LobbyResourceTest {

    @Mock
    LobbyService lobbyService;

    LobbyResource resource;

    @BeforeEach
    void setup() {
        resource = new LobbyResource(lobbyService);
    }

    @Test
    void shouldMapJoinConflictTo409() {
        when(lobbyService.joinLobby(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Already in lobby")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.joinLobby("ABC12", new JoinLobbyRequest("acc-1", "P")).await().indefinitely());

        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void shouldMapJoinNameConflictTo409() {
        when(lobbyService.joinLobby(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("That name is already taken in this lobby")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.joinLobby("ABC12", new JoinLobbyRequest("acc-1", "P")).await().indefinitely());

        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void shouldMapJoinFullTo409() {
        when(lobbyService.joinLobby(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Lobby is full")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.joinLobby("ABC12", new JoinLobbyRequest("acc-1", "P")).await().indefinitely());

        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void shouldMapJoinNotFoundTo404() {
        when(lobbyService.joinLobby(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Unable to find lobby with code: ABC12")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.joinLobby("ABC12", new JoinLobbyRequest("acc-1", "P")).await().indefinitely());

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    void shouldMapKickForbiddenTo403() {
        when(lobbyService.kickPlayer(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Only the host can kick players")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.kickPlayer("ABC12", new KickPlayerRequest("tok", 1)).await().indefinitely());

        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    void shouldMapKickNotFoundTo404() {
        when(lobbyService.kickPlayer(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Player at slot 99 not found in lobby ABC12")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.kickPlayer("ABC12", new KickPlayerRequest("tok", 99)).await().indefinitely());

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    void shouldMapKickConflictTo409() {
        when(lobbyService.kickPlayer(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("The host cannot be kicked from the lobby")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.kickPlayer("ABC12", new KickPlayerRequest("tok", 0)).await().indefinitely());

        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void shouldReturnKickedLobby() {
        Lobby lobby = new Lobby("ABC12", "pyramid", Map.of("cubeID", "cube-1"), "host-1",
                List.of(new LobbyPlayer("host-1", "Host", 0, "tok-host", Instant.now())),
                2, 2, null, LobbyStatus.WAITING, Instant.now(), Instant.now());

        when(lobbyService.kickPlayer(eq("ABC12"), any())).thenReturn(Uni.createFrom().item(lobby));

        Lobby result = resource.kickPlayer("ABC12", new KickPlayerRequest("tok-host", 1)).await().indefinitely();

        assertNotNull(result);
        assertEquals("ABC12", result.getLobbyCode());
    }

    @Test
    void shouldReturnJoinedLobby() {
        Lobby lobby = new Lobby("ABC12", "pyramid", Map.of("cubeID", "cube-1"), "host-1",
                List.of(new LobbyPlayer("host-1", "Host", 0, "tok-host", Instant.now())),
                2, 2, null, LobbyStatus.WAITING, Instant.now(), null);
        var response = new JoinLobbyResponse(lobby, "tok-new");

        when(lobbyService.joinLobby(eq("ABC12"), any())).thenReturn(Uni.createFrom().item(response));

        JoinLobbyResponse result = resource.joinLobby("ABC12", new JoinLobbyRequest("acc-2", "New"))
                .await().indefinitely();

        assertNotNull(result);
        assertEquals("tok-new", result.playerToken());
    }

    @Test
    void shouldMapLeaveNotFoundTo404() {
        when(lobbyService.leaveLobby(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Unable to find lobby with code: ABC12")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.leaveLobby("ABC12", new LeaveLobbyRequest("tok-1")).await().indefinitely());

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    void shouldReturnLeavingLobby() {
        Lobby lobby = new Lobby("ABC12", "pyramid", Map.of("cubeID", "cube-1"), "host-1",
                List.of(new LobbyPlayer("host-1", "Host", 0, "tok-host", Instant.now())),
                2, 2, null, LobbyStatus.WAITING, Instant.now(), null);

        when(lobbyService.leaveLobby(eq("ABC12"), any())).thenReturn(Uni.createFrom().item(lobby));

        Lobby result = resource.leaveLobby("ABC12", new LeaveLobbyRequest("tok-host")).await().indefinitely();

        assertNotNull(result);
        assertEquals("ABC12", result.getLobbyCode());
    }

    @Test
    void shouldMapGetLobbyNotFoundTo404() {
        when(lobbyService.getLobby(anyString(), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Unable to find lobby with code: ABC12")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.getLobby("ABC12", null).await().indefinitely());

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    void shouldReturnPolledLobby() {
        Lobby lobby = new Lobby("ABC12", "pyramid", Map.of("cubeID", "cube-1"), "host-1",
                List.of(new LobbyPlayer("host-1", "Host", 0, "tok-host", Instant.now())),
                2, 2, null, LobbyStatus.WAITING, Instant.now(), null);

        when(lobbyService.getLobby(anyString(), any())).thenReturn(Uni.createFrom().item(lobby));

        Lobby result = resource.getLobby("ABC12", null).await().indefinitely();

        assertNotNull(result);
        assertEquals("ABC12", result.getLobbyCode());
    }

    @Test
    void shouldMapStartForbiddenTo403() {
        when(lobbyService.startGame(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Only the host can start the game")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.startGame("ABC12", new StartGameRequest("tok")).await().indefinitely());

        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    void shouldMapStartNotEnoughPlayersTo400() {
        when(lobbyService.startGame(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(
                        new IllegalStateException("Not enough players to start (have 1, need 2)")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.startGame("ABC12", new StartGameRequest("tok")).await().indefinitely());

        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    void shouldMapStartAlreadyStartedTo409() {
        when(lobbyService.startGame(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Lobby ABC12 has already been started")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.startGame("ABC12", new StartGameRequest("tok")).await().indefinitely());

        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void shouldMapStartGameCreationFailureTo500() {
        when(lobbyService.startGame(eq("ABC12"), any()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Game creation failed: boom")));

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> resource.startGame("ABC12", new StartGameRequest("tok")).await().indefinitely());

        assertEquals(500, ex.getResponse().getStatus());
    }

    @Test
    void shouldReturnStartedLobby() {
        Lobby lobby = new Lobby("ABC12", "pyramid", Map.of("cubeID", "cube-1"), "host-1",
                List.of(new LobbyPlayer("host-1", "Host", 0, "tok-host", Instant.now())),
                2, 2, "game-123", LobbyStatus.STARTED, Instant.now(), null);

        when(lobbyService.startGame(eq("ABC12"), any())).thenReturn(Uni.createFrom().item(lobby));

        Lobby result = resource.startGame("ABC12", new StartGameRequest("tok")).await().indefinitely();

        assertNotNull(result);
        assertEquals("game-123", result.getGameID());
    }
}
