package org.magic.lobbyService;

import org.magic.lobbyService.api.CreateLobbyRequest;
import org.magic.lobbyService.api.JoinLobbyRequest;
import org.magic.lobbyService.api.JoinLobbyResponse;
import org.magic.lobbyService.api.KickPlayerRequest;
import org.magic.lobbyService.api.LeaveLobbyRequest;
import org.magic.lobbyService.api.Lobby;
import org.magic.lobbyService.api.StartGameRequest;
import org.magic.lobbyService.app.LobbyService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for managing draft-format-agnostic lobbies.
 * Supports creation, joining, leaving, polling, and game starting.
 */
@Path("/lobby")
@Produces(MediaType.APPLICATION_JSON)
public class LobbyResource {

    private final LobbyService lobbyService;

    public LobbyResource(final LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    /**
     * Creates a new lobby and adds the requesting user as the host.
     *
     * @param request the creation payload
     * @return the created lobby
     */
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> createLobby(final CreateLobbyRequest request) {
        return lobbyService.createLobby(request)
                .map(lobby -> Response.status(Response.Status.CREATED).entity(lobby).build());
    }

    /**
     * Joins an existing lobby that is in waiting status.
     *
     * @param lobbyCode the lobby to join
     * @param request   the join payload
     * @return the join response with the updated lobby and player token
     */
    @POST
    @Path("/{lobbyCode}/join")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<JoinLobbyResponse> joinLobby(@PathParam("lobbyCode") final String lobbyCode,
                                             final JoinLobbyRequest request) {
        return lobbyService.joinLobby(lobbyCode, request)
                .onFailure(IllegalStateException.class)
                .transform(t -> new WebApplicationException(t.getMessage(),
                        isJoinConflict(t) ? Response.Status.CONFLICT : Response.Status.NOT_FOUND));
    }

    /**
     * Kicks a player from a waiting lobby. Only the host may kick players.
     *
     * @param lobbyCode the lobby to kick from
     * @param request   the kick payload with the host's account ID and the target's token
     * @return the updated lobby
     */
    @POST
    @Path("/{lobbyCode}/kick")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Lobby> kickPlayer(@PathParam("lobbyCode") final String lobbyCode,
                                  final KickPlayerRequest request) {
        return lobbyService.kickPlayer(lobbyCode, request)
                .onFailure(IllegalStateException.class)
                .transform(t -> toKickException(t));
    }

    /**
     * Leaves a lobby. If the leaving player was the host, a new host is assigned.
     *
     * @param lobbyCode the lobby to leave
     * @param request   identifies the player to remove
     * @return the updated lobby
     */
    @POST
    @Path("/{lobbyCode}/leave")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Lobby> leaveLobby(@PathParam("lobbyCode") final String lobbyCode,
                                  final LeaveLobbyRequest request) {
        return lobbyService.leaveLobby(lobbyCode, request)
                .onFailure(IllegalStateException.class)
                .transform(t -> new WebApplicationException(t.getMessage(), Response.Status.NOT_FOUND));
    }

    /**
     * Polls the current lobby state. A player token may be provided to update
     * the player's heartbeat timestamp.
     *
     * @param lobbyCode   the lobby to poll
     * @param playerToken the token of the polling player, or {@code null}
     * @return the current lobby state
     */
    @GET
    @Path("/{lobbyCode}")
    public Uni<Lobby> getLobby(@PathParam("lobbyCode") final String lobbyCode,
                                @QueryParam("playerToken") final String playerToken) {
        return lobbyService.getLobby(lobbyCode, playerToken)
                .onFailure(IllegalStateException.class)
                .transform(t -> new WebApplicationException(t.getMessage(), Response.Status.NOT_FOUND));
    }

    /**
     * Starts the game for a lobby. Only the host may start the game,
     * and the lobby must have at least the minimum number of players.
     *
     * @param lobbyCode the lobby to start
     * @param request   the start request with the host's account ID
     * @return the updated lobby with the game ID populated
     */
    @POST
    @Path("/{lobbyCode}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Lobby> startGame(@PathParam("lobbyCode") final String lobbyCode,
                                 final StartGameRequest request) {
        return lobbyService.startGame(lobbyCode, request)
                .onFailure(IllegalStateException.class)
                .transform(t -> toStartGameException(t));
    }

    private static boolean isJoinConflict(final Throwable t) {
        String msg = t.getMessage();
        return msg != null && (msg.contains("Already in lobby")
                || msg.contains("name is already taken")
                || msg.contains("Lobby is full")
                || msg.contains("no longer accepting")
                || msg.contains("cannot be blank"));
    }

    private static WebApplicationException toKickException(final Throwable t) {
        String msg = t.getMessage();
        if (msg != null && msg.contains("Only the host")) {
            return new WebApplicationException(msg, Response.Status.FORBIDDEN);
        }
        if (msg != null && (msg.contains("Unable to find lobby") || msg.contains("not found in lobby"))) {
            return new WebApplicationException(msg, Response.Status.NOT_FOUND);
        }
        return new WebApplicationException(msg, Response.Status.CONFLICT);
    }

    private static WebApplicationException toStartGameException(final Throwable t) {
        String msg = t.getMessage();
        if (msg != null && msg.contains("Only the host")) {
            return new WebApplicationException(msg, Response.Status.FORBIDDEN);
        }
        if (msg != null && msg.contains("Not enough players")) {
            return new WebApplicationException(msg, Response.Status.BAD_REQUEST);
        }
        if (msg != null && msg.contains("already been started")) {
            return new WebApplicationException(msg, Response.Status.CONFLICT);
        }
        if (msg != null && msg.contains("Game creation failed")) {
            return new WebApplicationException(msg, Response.Status.INTERNAL_SERVER_ERROR);
        }
        return new WebApplicationException(msg, Response.Status.BAD_REQUEST);
    }
}
