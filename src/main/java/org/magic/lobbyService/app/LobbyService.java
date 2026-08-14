package org.magic.lobbyService.app;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.lobbyService.api.CreateLobbyRequest;
import org.magic.lobbyService.api.JoinLobbyRequest;
import org.magic.lobbyService.api.JoinLobbyResponse;
import org.magic.lobbyService.api.KickPlayerRequest;
import org.magic.lobbyService.api.LeaveLobbyRequest;
import org.magic.lobbyService.api.Lobby;
import org.magic.lobbyService.api.LobbyPlayer;
import org.magic.lobbyService.api.LobbyStatus;
import org.magic.lobbyService.api.StartGameRequest;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Service layer for lobby management. Handles creation, joining, leaving,
 * polling, game starting, and periodic heartbeat cleanup of stale lobbies.
 */
@ApplicationScoped
public class LobbyService {
    private static final Logger LOGGER = LogManager.getLogger(LobbyService.class);

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_CODE_ATTEMPTS = 10;
    private static final Duration STALE_PLAYER_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration HOST_ALONE_TIMEOUT = Duration.ofMinutes(5);
    private static final java.util.regex.Pattern CUBE_ID_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LobbyDbHandler dbHandler;
    private final Instance<DraftTypeHandler> draftTypeHandlers;

    @Inject
    public LobbyService(final LobbyDbHandler dbHandler, final Instance<DraftTypeHandler> draftTypeHandlers) {
        this.dbHandler = dbHandler;
        this.draftTypeHandlers = draftTypeHandlers;
    }

    /**
     * Creates a new lobby, generates a unique 8-character code, and adds the host
     * as the first player. The host's lobby token is returned to the creator so it
     * can be used for polling, kicking, and starting the game.
     *
     * @param request the creation payload
     * @return the created {@link Lobby} plus the host's token
     */
    public Uni<JoinLobbyResponse> createLobby(final CreateLobbyRequest request) {
        return Uni.createFrom().item(() -> {
            String code = generateUniqueCode();

            DraftTypeHandler handler = findHandler(request.draftType());
            int minPlayers = request.minPlayers() != null ? request.minPlayers() : handler.defaultMinPlayers();
            int maxPlayers = request.maxPlayers() != null ? request.maxPlayers() : handler.defaultMaxPlayers();

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
            return new JoinLobbyResponse(lobby, playerToken);
        });
    }

    /**
     * Adds a player to an existing waiting lobby. Generates a unique player token
     * used for subsequent poll and leave operations. Rejects duplicate accounts,
     * duplicate display names (trimmed, case-insensitive), blank names, and full lobbies.
     *
     * @param lobbyCode the lobby to join
     * @param request   the join payload
     * @return the updated lobby and the player's token
     */
    public Uni<JoinLobbyResponse> joinLobby(final String lobbyCode, final JoinLobbyRequest request) {
        return Uni.createFrom().item(() -> {
            Lobby lobby = dbHandler.findByCode(lobbyCode);

            String displayName = request.displayName().trim();
            if (displayName.isEmpty()) {
                throw new IllegalStateException("displayName cannot be blank");
            }

            if (lobby.getStatus() != LobbyStatus.WAITING) {
                throw new IllegalStateException("Lobby " + lobbyCode + " is no longer accepting players");
            }

            if (request.accountID() != null) {
                boolean alreadyJoined = lobby.getPlayers().stream()
                        .anyMatch(p -> request.accountID().equals(p.getAccountID()));
                if (alreadyJoined) {
                    throw new IllegalStateException("Already in lobby");
                }
            }

            boolean nameTaken = lobby.getPlayers().stream()
                    .map(LobbyPlayer::getDisplayName)
                    .filter(Objects::nonNull)
                    .anyMatch(existing -> displayName.equalsIgnoreCase(existing.trim()));
            if (nameTaken) {
                throw new IllegalStateException("That name is already taken in this lobby");
            }

            if (lobby.getPlayers().size() >= lobby.getMaxPlayers()) {
                throw new IllegalStateException("Lobby is full");
            }

            int slotIndex = findNextSlotIndex(lobby.getPlayers());
            String playerToken = UUID.randomUUID().toString();
            Instant now = Instant.now();

            LobbyPlayer newPlayer = new LobbyPlayer(
                    request.accountID(),
                    displayName,
                    slotIndex,
                    playerToken,
                    now
            );

            lobby.getPlayers().add(newPlayer);
            lobby.setHostAloneSince(null);
            dbHandler.updateLobby(lobby);

            LOGGER.info("Player {} joined lobby {} at slot {}", displayName, lobbyCode, slotIndex);
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

            LobbyPlayer player = findPlayerByToken(lobby, request.playerToken());

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
     * Kicks a player from a waiting lobby. Only the host may kick players, proven
     * by presenting the host's lobby token. The host themselves can never be kicked.
     * When the kick leaves the host alone in the lobby, the {@code hostAloneSince}
     * clock is started so the heartbeat can eventually clean up the empty lobby.
     *
     * @param lobbyCode the lobby to kick from
     * @param request   the caller's own token and the target's slot index
     * @return the updated lobby
     */
    public Uni<Lobby> kickPlayer(final String lobbyCode, final KickPlayerRequest request) {
        return Uni.createFrom().item(() -> {
            Lobby lobby = dbHandler.findByCode(lobbyCode);

            if (!isCallerHost(lobby, request.playerToken())) {
                throw new IllegalStateException("Only the host can kick players");
            }

            LobbyPlayer target = lobby.getPlayers().stream()
                    .filter(p -> p.getSlotIndex() == request.targetSlotIndex())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Player at slot " + request.targetSlotIndex() + " not found in lobby " + lobbyCode));

            if (isHost(lobby, target)) {
                throw new IllegalStateException("The host cannot be kicked from the lobby");
            }

            if (lobby.getStatus() != LobbyStatus.WAITING) {
                throw new IllegalStateException("Cannot kick players from lobby " + lobbyCode + " in status " + lobby.getStatus());
            }

            lobby.getPlayers().remove(target);

            if (onlyHostRemains(lobby)) {
                lobby.setHostAloneSince(Instant.now());
            }

            dbHandler.updateLobby(lobby);
            LOGGER.info("Player {} kicked from lobby {}", target.getDisplayName(), lobbyCode);
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
     * Starts the game for a lobby. Validates the host identity (proven by the
     * host's lobby token) and player count, resolves the {@link DraftTypeHandler}
     * for the lobby's draft type, and delegates game creation to that handler.
     *
     * @param lobbyCode the lobby to start
     * @param request   the caller's own lobby token
     * @return the updated lobby with a populated {@code gameID}
     */
    public Uni<Lobby> startGame(final String lobbyCode, final StartGameRequest request) {
        return Uni.createFrom().item(() -> {
            Lobby lobby = dbHandler.findByCode(lobbyCode);

            if (lobby.getStatus() != LobbyStatus.WAITING) {
                throw new IllegalStateException("Lobby " + lobbyCode + " has already been started");
            }

            if (!isCallerHost(lobby, request.playerToken())) {
                throw new IllegalStateException("Only the host can start the game");
            }

            if (lobby.getPlayers().size() < lobby.getMinPlayers()) {
                throw new IllegalStateException("Not enough players to start (have " +
                        lobby.getPlayers().size() + ", need " + lobby.getMinPlayers() + ")");
            }

            return lobby;
        }).chain(lobby -> {
            lobby.setStatus(LobbyStatus.STARTING);
            dbHandler.updateLobby(lobby);

            String cubeID = (String) lobby.getConfig().get("cubeID");
            if (cubeID == null) {
                lobby.setStatus(LobbyStatus.WAITING);
                dbHandler.updateLobby(lobby);
                throw new IllegalStateException("cubeID is required in lobby config");
            }
            if (!CUBE_ID_PATTERN.matcher(cubeID).matches()) {
                lobby.setStatus(LobbyStatus.WAITING);
                dbHandler.updateLobby(lobby);
                throw new IllegalStateException("cubeID in lobby config is invalid");
            }

            DraftTypeHandler handler;
            try {
                handler = findHandler(lobby.getDraftType());
            } catch (IllegalStateException e) {
                lobby.setStatus(LobbyStatus.WAITING);
                dbHandler.updateLobby(lobby);
                throw e;
            }

            List<LobbyPlayer> sortedPlayers = lobby.getPlayers().stream()
                    .sorted(Comparator.comparingInt(LobbyPlayer::getSlotIndex))
                    .toList();

            return handler.startGame(cubeID, sortedPlayers, lobby.getConfig())
                    .map(gameID -> {
                        lobby.setGameID(gameID);
                        lobby.setStatus(LobbyStatus.STARTED);
                        dbHandler.updateLobby(lobby);
                        LOGGER.info("Lobby {} started game {}", lobbyCode, gameID);
                        return lobby;
                    })
                    .onFailure().transform(t -> {
                        lobby.setStatus(LobbyStatus.WAITING);
                        dbHandler.updateLobby(lobby);
                        LOGGER.error("Failed to start game for lobby {}: {}", lobbyCode, t.getMessage());
                        return new IllegalStateException("Game creation failed: " + t.getMessage());
                    });
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
            Optional<LobbyPlayer> hostBefore = findHostPlayer(lobby);
            List<LobbyPlayer> toRemove = lobby.getPlayers().stream()
                    .filter(p -> p.getLastPollAt() == null || p.getLastPollAt().isBefore(staleCutoff))
                    .toList();

            boolean hostRemoved = hostBefore.map(toRemove::contains).orElse(false);

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

            if (hostRemoved) {
                lobby.getPlayers().sort((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()));
                LobbyPlayer newHost = lobby.getPlayers().get(0);
                lobby.setHostAccountID(newHost.getAccountID() != null
                        ? newHost.getAccountID() : guestHostId(newHost.getPlayerToken()));
                lobby.setHostAloneSince(null);
                LOGGER.info("Heartbeat: reassigned host of lobby {} to slot {}",
                        lobby.getLobbyCode(), newHost.getSlotIndex());
            }

            if (onlyHostRemains(lobby)) {
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

    private DraftTypeHandler findHandler(final String draftType) {
        return draftTypeHandlers.stream()
                .filter(handler -> handler.draftType().equalsIgnoreCase(draftType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unsupported draft type: " + draftType));
    }

    private static int findNextSlotIndex(final List<LobbyPlayer> players) {
        return players.stream()
                .mapToInt(LobbyPlayer::getSlotIndex)
                .max()
                .orElse(-1) + 1;
    }

    /**
     * Locates a player by their lobby token. A token is always assigned on create
     * or join, and its possession is the proof of identity required to leave a
     * lobby — a raw account ID alone can never be used to remove another player.
     */
    private static LobbyPlayer findPlayerByToken(final Lobby lobby, final String playerToken) {
        if (playerToken == null || playerToken.isBlank()) {
            throw new IllegalStateException("playerToken is required to leave the lobby");
        }
        return lobby.getPlayers().stream()
                .filter(p -> playerToken.equals(p.getPlayerToken()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No player in lobby " + lobby.getLobbyCode() + " matches the provided token"));
    }

    /**
     * Determines whether a player is the lobby host. Handles both authenticated hosts
     * (matched by account ID) and guest hosts (whose {@code hostAccountID} is stored
     * as a hashed {@code guest-<digest>} marker to avoid leaking the bearer token).
     */
    private static boolean isHost(final Lobby lobby, final LobbyPlayer player) {
        String hostID = lobby.getHostAccountID();
        if (hostID == null) {
            return false;
        }
        if (hostID.equals(player.getAccountID())) {
            return true;
        }
        return player.getPlayerToken() != null && hostID.equals(guestHostId(player.getPlayerToken()));
    }

    /**
     * Locates the current host player in the lobby, if any. Authenticated hosts are
     * matched by account ID; guest hosts are matched by the hashed {@code guest-<digest>}
     * marker stored in {@code hostAccountID}.
     */
    private static Optional<LobbyPlayer> findHostPlayer(final Lobby lobby) {
        return lobby.getPlayers().stream()
                .filter(player -> isHost(lobby, player))
                .findFirst();
    }

    /**
     * Verifies that the presented lobby token belongs to the current host. Host
     * identity is never taken from the request body — it must be proven by possession
     * of the host's token.
     */
    private static boolean isCallerHost(final Lobby lobby, final String callerToken) {
        if (callerToken == null) {
            return false;
        }
        return findHostPlayer(lobby)
                .map(host -> callerToken.equals(host.getPlayerToken()))
                .orElse(false);
    }

    /**
     * Produces a non-reversible host marker for a guest-host lobby so the player's
     * bearer token is never stored or serialized in plain text.
     */
    private static String guestHostId(final String playerToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(playerToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "guest-" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static boolean onlyHostRemains(final Lobby lobby) {
        if (lobby.getPlayers().size() != 1) {
            return false;
        }
        return findHostPlayer(lobby)
                .map(host -> host == lobby.getPlayers().get(0))
                .orElse(false);
    }
}
