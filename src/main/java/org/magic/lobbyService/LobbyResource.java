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
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST resource for managing draft-format-agnostic lobbies.
 * Supports creation, joining, leaving, polling, and game starting.
 */
@Path("/lobby")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Lobby", description = "Draft-format-agnostic lobbies: create, join, leave, poll, kick, and start.")
public class LobbyResource {

    private final LobbyService lobbyService;

    public LobbyResource(final LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    /**
     * Creates a new lobby and adds the requesting user as the host.
     * Returns the created lobby along with the host's lobby token, which the
     * caller must retain for polling, kicking, and starting the game.
     *
     * @param request the creation payload
     * @return 201 Created with the {@link JoinLobbyResponse}
     */
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a lobby",
            description = "Creates a new lobby and adds the caller as host. Returns the lobby and the host's lobby " +
                    "token, which must be retained for polling, kicking, and starting. Returns 400 for an " +
                    "unsupported draft type.")
    public Uni<Response> createLobby(@Valid final CreateLobbyRequest request) {
        return lobbyService.createLobby(request)
                .map(response -> Response.status(Response.Status.CREATED).entity(response).build())
                .onFailure(IllegalStateException.class)
                .transform(t -> {
                    if (t.getMessage() != null && t.getMessage().contains("Unsupported draft type")) {
                        return new WebApplicationException(t.getMessage(), Response.Status.BAD_REQUEST);
                    }
                    return new WebApplicationException(t.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
                });
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
    @Operation(summary = "Join a lobby",
            description = "Joins a waiting lobby. Returns the updated lobby and the player's lobby token. " +
                    "Returns 409 on conflicts (already in lobby, name taken, full) and 404 if the lobby is missing.")
    public Uni<JoinLobbyResponse> joinLobby(@PathParam("lobbyCode") @Size(max = 8) final String lobbyCode,
                                             @Valid final JoinLobbyRequest request) {
        return lobbyService.joinLobby(lobbyCode, request)
                .onFailure(IllegalStateException.class)
                .transform(t -> new WebApplicationException(t.getMessage(),
                        isJoinConflict(t) ? Response.Status.CONFLICT : Response.Status.NOT_FOUND));
    }

    /**
     * Kicks a player from a waiting lobby. Only the host may kick players,
     * proven by presenting the host's lobby token.
     *
     * @param lobbyCode the lobby to kick from
     * @param request   the caller's own token and the target's slot index
     * @return the updated lobby
     */
    @POST
    @Path("/{lobbyCode}/kick")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Kick a player",
            description = "Kicks the player at the given slot index from a waiting lobby. Only the host may kick, " +
                    "proven by the host's lobby token. Returns 403 for non-hosts, 404 for missing lobbies or slots.")
    public Uni<Lobby> kickPlayer(@PathParam("lobbyCode") @Size(max = 8) final String lobbyCode,
                                  @Valid final KickPlayerRequest request) {
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
    @Operation(summary = "Leave a lobby",
            description = "Removes the identified player from a lobby; a new host is assigned if the host leaves. " +
                    "Returns 404 if the lobby or player is missing.")
    public Uni<Lobby> leaveLobby(@PathParam("lobbyCode") @Size(max = 8) final String lobbyCode,
                                  @Valid final LeaveLobbyRequest request) {
        return lobbyService.leaveLobby(lobbyCode, request)
                .onFailure(IllegalStateException.class)
                .transform(t -> new WebApplicationException(t.getMessage(), Response.Status.NOT_FOUND));
    }

    /**
     * Polls the current lobby state. A player token may be provided via the
     * {@code X-Player-Token} header to update the player's heartbeat timestamp.
     *
     * @param lobbyCode   the lobby to poll
     * @param playerToken the token of the polling player, or {@code null}
     * @return the current lobby state
     */
    @GET
    @Path("/{lobbyCode}")
    @Operation(summary = "Poll a lobby",
            description = "Returns the current lobby state. Optionally send the X-Player-Token header to update " +
                    "the player's heartbeat. Returns 404 if the lobby is missing.")
    public Uni<Lobby> getLobby(@PathParam("lobbyCode") @Size(max = 8) final String lobbyCode,
                               @HeaderParam("X-Player-Token") final String playerToken) {
        return lobbyService.getLobby(lobbyCode, playerToken)
                .onFailure(IllegalStateException.class)
                .transform(t -> new WebApplicationException(t.getMessage(), Response.Status.NOT_FOUND));
    }

    /**
     * Starts the game for a lobby. Only the host may start the game, proven by
     * presenting the host's lobby token, and the lobby must have at least the
     * minimum number of players.
     *
     * @param lobbyCode the lobby to start
     * @param request   the caller's own lobby token
     * @return the updated lobby with the game ID populated
     */
    @POST
    @Path("/{lobbyCode}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Start the game",
            description = "Starts the draft for a waiting lobby. Only the host may start. Returns 403 for non-hosts, " +
                    "400 when player counts are insufficient, 409 if already started.")
    public Uni<Lobby> startGame(@PathParam("lobbyCode") @Size(max = 8) final String lobbyCode,
                                 @Valid final StartGameRequest request) {
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
