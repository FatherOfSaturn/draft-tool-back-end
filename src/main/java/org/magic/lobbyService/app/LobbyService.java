package org.magic.lobbyService.app;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.lobbyService.api.CreateLobbyRequest;
import org.magic.lobbyService.api.JoinLobbyRequest;
import org.magic.lobbyService.api.JoinLobbyResponse;
import org.magic.lobbyService.api.LeaveLobbyRequest;
import org.magic.lobbyService.api.Lobby;
import org.magic.lobbyService.api.LobbyPlayer;
import org.magic.lobbyService.api.LobbyStatus;
import org.magic.lobbyService.api.StartGameRequest;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.PlayerCreationInfo;
import org.magic.pyramidDraft.app.GameCoordination.GameCoordinationWorker;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service layer for lobby management. Handles creation, joining, leaving,
 * polling, game starting, and periodic heartbeat cleanup of stale lobbies.
 */
@ApplicationScoped
public class LobbyService {
    private static final Logger LOGGER = LogManager.getLogger(LobbyService.class);

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 5;
    private static final int MAX_CODE_ATTEMPTS = 10;
    private static final Duration STALE_PLAYER_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration HOST_ALONE_TIMEOUT = Duration.ofMinutes(5);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LobbyDbHandler dbHandler;
    private final GameCoordinationWorker gameWorker;

    @Inject
    public LobbyService(final LobbyDbHandler dbHandler, final GameCoordinationWorker gameWorker) {
        this.dbHandler = dbHandler;
        this.gameWorker = gameWorker;
    }

    /**
     * Creates a new lobby, generates a unique 5-character code, and adds the host
     * as the first player.
     *
     * @param request the creation payload
     * @return the created {@link Lobby}
     */
    public Uni<Lobby> createLobby(final CreateLobbyRequest request) {
        return Uni.createFrom().item(() -> {
            String code = generateUniqueCode();

            int minPlayers = request.minPlayers() != null ? request.minPlayers() : getMinPlayers(request.draftType());
            int maxPlayers = request.maxPlayers() != null ? request.maxPlayers() : getMaxPlayers(request.draftType());

            String playerToken = UUID.randomUUID().toString();
            Instant now = Instant.now();

            LobbyPlayer host = new LobbyPlayer(
                    request.hostAccountID(),
                    request.hostDisplayName(),
                    0,
                    playerToken,
                    now
            );

            Lobby lobby = new Lobby(
                    code,
                    request.draftType(),
                    request.config(),
                    request.hostAccountID(),
                    new ArrayList<>(List.of(host)),
                    minPlayers,
                    maxPlayers,
                    null,
                    LobbyStatus.WAITING,
                    now,
                    null
            );

            dbHandler.insertLobby(lobby);
            LOGGER.info("Created lobby {} for draft type {}", code, request.draftType());
            return lobby;
        });
    }

    /**
     * Adds a player to an existing waiting lobby. Generates a unique player token
     * used for subsequent poll and leave operations.
     *
     * @param lobbyCode the lobby to join
     * @param request   the join payload
     * @return the updated lobby and the player's token
     */
    public Uni<JoinLobbyResponse> joinLobby(final String lobbyCode, final JoinLobbyRequest request) {
        return Uni.createFrom().item(() -> {
            Lobby lobby = dbHandler.findByCode(lobbyCode);

            if (lobby.getStatus() != LobbyStatus.WAITING) {
                throw new IllegalStateException("Lobby " + lobbyCode + " is no longer accepting players");
            }

            if (request.accountID() != null) {
                boolean alreadyJoined = lobby.getPlayers().stream()
                        .anyMatch(p -> request.accountID().equals(p.getAccountID()));
                if (alreadyJoined) {
                    throw new IllegalStateException("Player " + request.accountID() + " is already in lobby " + lobbyCode);
                }
            }

            if (lobby.getPlayers().size() >= lobby.getMaxPlayers()) {
                throw new IllegalStateException("Lobby " + lobbyCode + " is full");
            }

            int slotIndex = findNextSlotIndex(lobby.getPlayers());
            String playerToken = UUID.randomUUID().toString();
            Instant now = Instant.now();

            LobbyPlayer newPlayer = new LobbyPlayer(
                    request.accountID(),
                    request.displayName(),
                    slotIndex,
                    playerToken,
                    now
            );

            lobby.getPlayers().add(newPlayer);
            lobby.setHostAloneSince(null);
            dbHandler.updateLobby(lobby);

            LOGGER.info("Player {} joined lobby {} at slot {}", request.displayName(), lobbyCode, slotIndex);
            return new JoinLobbyResponse(lobby, playerToken);
        });
    }

    /**
     * Removes a player from a waiting lobby. If the leaving player was the host,
     * a new host is assigned to the player with the lowest slot index.
     *
     * @param lobbyCode the lobby to leave
     * @param request   identifies the leaving player
     * @return the updated lobby
     */
    public Uni<Lobby> leaveLobby(final String lobbyCode, final LeaveLobbyRequest request) {
        return Uni.createFrom().item(() -> {
            Lobby lobby = dbHandler.findByCode(lobbyCode);

            if (lobby.getStatus() != LobbyStatus.WAITING) {
                throw new IllegalStateException("Cannot leave lobby " + lobbyCode + " in status " + lobby.getStatus());
            }

            LobbyPlayer player = findPlayer(lobby, request.accountID(), request.playerToken());

            lobby.getPlayers().remove(player);

            boolean hostLeft = player.getAccountID() != null && player.getAccountID().equals(lobby.getHostAccountID());

            if (lobby.getPlayers().isEmpty()) {
                dbHandler.deleteLobby(lobbyCode);
                LOGGER.info("Deleted lobby {} (no players remaining)", lobbyCode);
                return lobby;
            }

            if (hostLeft) {
                lobby.getPlayers().sort((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()));
                LobbyPlayer newHost = lobby.getPlayers().get(0);
                lobby.setHostAccountID(newHost.getAccountID());
                LOGGER.info("Reassigned host of lobby {} to player at slot {}", lobbyCode, newHost.getSlotIndex());
            }

            dbHandler.updateLobby(lobby);
            return lobby;
        });
    }

    /**
     * Polls the current lobby state and updates the polling player's {@code lastPollAt}
     * timestamp for heartbeat tracking.
     *
     * @param lobbyCode   the lobby to poll
     * @param playerToken the token of the polling player, or {@code null}
     * @return the current {@link Lobby}
     */
    public Uni<Lobby> getLobby(final String lobbyCode, final String playerToken) {
        return Uni.createFrom().item(() -> {
            Lobby lobby = dbHandler.findByCode(lobbyCode);

            if (playerToken != null) {
                lobby.getPlayers().stream()
                        .filter(p -> playerToken.equals(p.getPlayerToken()))
                        .findFirst()
                        .ifPresent(p -> p.setLastPollAt(Instant.now()));
            }

            return lobby;
        });
    }

    /**
     * Starts the game for a lobby. Validates the host identity and player count,
     * assembles a {@link GameCreationInfo}, and delegates to the existing game service.
     *
     * @param lobbyCode the lobby to start
     * @param request   the start request with the host's account ID
     * @return the updated lobby with a populated {@code gameID}
     */
    public Uni<Lobby> startGame(final String lobbyCode, final StartGameRequest request) {
        Lobby lobby = dbHandler.findByCode(lobbyCode);

        if (lobby.getStatus() != LobbyStatus.WAITING) {
            throw new IllegalStateException("Lobby " + lobbyCode + " has already been started");
        }

        if (!request.hostAccountID().equals(lobby.getHostAccountID())) {
            throw new IllegalStateException("Only the host can start the game");
        }

        if (lobby.getPlayers().size() < lobby.getMinPlayers()) {
            throw new IllegalStateException("Not enough players to start (have " +
                    lobby.getPlayers().size() + ", need " + lobby.getMinPlayers() + ")");
        }

        lobby.setStatus(LobbyStatus.STARTING);
        dbHandler.updateLobby(lobby);

        String gameID = UUID.randomUUID().toString();
        String cubeID = (String) lobby.getConfig().get("cubeID");
        if (cubeID == null) {
            lobby.setStatus(LobbyStatus.WAITING);
            dbHandler.updateLobby(lobby);
            throw new IllegalStateException("cubeID is required in lobby config");
        }

        int doubleDraftPicks = 0;
        Object picksVal = lobby.getConfig().get("numberOfDoubleDraftPicksPerPlayer");
        if (picksVal instanceof Number) {
            doubleDraftPicks = ((Number) picksVal).intValue();
        }

        List<PlayerCreationInfo> players = lobby.getPlayers().stream()
                .map(p -> new PlayerCreationInfo(
                        p.getDisplayName(),
                        p.getAccountID() != null ? p.getAccountID() : "guest-" + p.getPlayerToken()))
                .toList();

        GameCreationInfo gameCreationInfo = new GameCreationInfo(gameID, cubeID, players, doubleDraftPicks);

        return gameWorker.startGame(gameCreationInfo)
                .map(gameInfo -> {
                    lobby.setGameID(gameInfo.getGameID());
                    lobby.setStatus(LobbyStatus.STARTED);
                    dbHandler.updateLobby(lobby);
                    LOGGER.info("Lobby {} started game {}", lobbyCode, gameInfo.getGameID());
                    return lobby;
                })
                .onFailure().recoverWithItem(t -> {
                    lobby.setStatus(LobbyStatus.WAITING);
                    dbHandler.updateLobby(lobby);
                    LOGGER.error("Failed to start game for lobby {}: {}", lobbyCode, t.getMessage());
                    throw new IllegalStateException("Game creation failed: " + t.getMessage());
                });
    }

    /**
     * Periodic heartbeat that removes stale players from waiting lobbies.
     * A player is considered stale if their {@code lastPollAt} is older than 60 seconds.
     * If the host was among the stale players, a new host is assigned.
     * Empty lobbies are deleted immediately.
     * Lobbies where the host is alone for more than 5 minutes are auto-deleted.
     */
    @Scheduled(every = "PT15S")
    void heartbeatCleanup() {
        List<Lobby> lobbies = dbHandler.findLobbiesByStatus(LobbyStatus.WAITING);
        Instant staleCutoff = Instant.now().minus(STALE_PLAYER_TIMEOUT);
        Instant aloneCutoff = Instant.now().minus(HOST_ALONE_TIMEOUT);

        for (Lobby lobby : lobbies) {
            List<LobbyPlayer> toRemove = lobby.getPlayers().stream()
                    .filter(p -> p.getLastPollAt() == null || p.getLastPollAt().isBefore(staleCutoff))
                    .toList();

            if (!toRemove.isEmpty()) {
                LOGGER.info("Heartbeat: removing {} stale player(s) from lobby {}",
                        toRemove.size(), lobby.getLobbyCode());
                lobby.getPlayers().removeAll(toRemove);
            }

            if (lobby.getPlayers().isEmpty()) {
                dbHandler.deleteLobby(lobby.getLobbyCode());
                LOGGER.info("Heartbeat: deleted empty lobby {}", lobby.getLobbyCode());
                continue;
            }

            String hostID = lobby.getHostAccountID();
            boolean hostRemoved = toRemove.stream()
                    .anyMatch(p -> hostID != null && hostID.equals(p.getAccountID()));

            if (hostRemoved && !lobby.getPlayers().isEmpty()) {
                lobby.getPlayers().sort((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()));
                LobbyPlayer newHost = lobby.getPlayers().get(0);
                lobby.setHostAccountID(newHost.getAccountID() != null
                        ? newHost.getAccountID() : "guest-" + newHost.getPlayerToken());
                lobby.setHostAloneSince(null);
                LOGGER.info("Heartbeat: reassigned host of lobby {} to slot {}",
                        lobby.getLobbyCode(), newHost.getSlotIndex());
            }

            boolean onlyHost = lobby.getPlayers().size() == 1
                    && lobby.getPlayers().get(0).getAccountID() != null
                    && lobby.getPlayers().get(0).getAccountID().equals(lobby.getHostAccountID());

            if (onlyHost) {
                if (lobby.getHostAloneSince() == null) {
                    lobby.setHostAloneSince(Instant.now());
                } else if (lobby.getHostAloneSince().isBefore(aloneCutoff)) {
                    dbHandler.deleteLobby(lobby.getLobbyCode());
                    LOGGER.info("Heartbeat: deleted lobby {} (host alone > 5 min)", lobby.getLobbyCode());
                    continue;
                }
            } else {
                lobby.setHostAloneSince(null);
            }

            if (!toRemove.isEmpty() || hostRemoved) {
                dbHandler.updateLobby(lobby);
            }
        }
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = generateCode();
            if (!dbHandler.lobbyExists(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate unique lobby code after " + MAX_CODE_ATTEMPTS + " attempts");
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private static int getMinPlayers(final String draftType) {
        return "pyramid".equalsIgnoreCase(draftType) ? 2 : 4;
    }

    private static int getMaxPlayers(final String draftType) {
        return "pyramid".equalsIgnoreCase(draftType) ? 2 : 12;
    }

    private static int findNextSlotIndex(final List<LobbyPlayer> players) {
        return players.stream()
                .mapToInt(LobbyPlayer::getSlotIndex)
                .max()
                .orElse(-1) + 1;
    }

    private static LobbyPlayer findPlayer(final Lobby lobby, final String accountID, final String playerToken) {
        if (accountID != null) {
            return lobby.getPlayers().stream()
                    .filter(p -> accountID.equals(p.getAccountID()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Player with accountID " + accountID + " not found in lobby " + lobby.getLobbyCode()));
        }
        if (playerToken != null) {
            return lobby.getPlayers().stream()
                    .filter(p -> playerToken.equals(p.getPlayerToken()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Player with token " + playerToken + " not found in lobby " + lobby.getLobbyCode()));
        }
        throw new IllegalStateException("accountID or playerToken is required to identify the player");
    }
}
