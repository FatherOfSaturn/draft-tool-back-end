package org.magic.lobbyService.app;

import java.util.List;
import java.util.Map;

import org.magic.lobbyService.api.LobbyPlayer;

import io.smallrye.mutiny.Uni;

/**
 * Strategy for starting a game of a specific draft format from a lobby.
 * Each supported draft type provides a handler bean that knows how to translate
 * a lobby's player list and opaque config into the game service for that format.
 */
public interface DraftTypeHandler {

    /**
     * @return the draft type identifier this handler supports (e.g. "pyramid", "classic")
     */
    String draftType();

    /**
     * @return the default minimum player count for this draft type
     */
    int defaultMinPlayers();

    /**
     * @return the default maximum player count for this draft type
     */
    int defaultMaxPlayers();

    /**
     * Starts a game for the given draft type. The game ID is always generated
     * by the backend and returned once the game has been created.
     *
     * @param cubeID  the cube to draft from
     * @param players the lobby players in draft-seat order
     * @param config  the opaque lobby config for this draft type
     * @return a {@link Uni} emitting the generated game ID
     */
    Uni<String> startGame(String cubeID, List<LobbyPlayer> players, Map<String, Object> config);
}
