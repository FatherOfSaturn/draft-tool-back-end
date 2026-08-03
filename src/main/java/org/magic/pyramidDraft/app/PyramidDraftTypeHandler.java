package org.magic.pyramidDraft.app;

import java.util.List;
import java.util.Map;

import org.magic.lobbyService.api.LobbyPlayer;
import org.magic.lobbyService.app.DraftTypeHandler;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.PlayerCreationInfo;
import org.magic.pyramidDraft.app.GameCoordination.GameCoordinationWorker;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * {@link DraftTypeHandler} that boots a pyramid draft game from a lobby.
 * Anonymous lobby players (no account ID) are mapped to a stable guest ID so
 * the pyramid player creation validation is satisfied.
 */
@ApplicationScoped
public class PyramidDraftTypeHandler implements DraftTypeHandler {

    private final GameCoordinationWorker gameWorker;

    @Inject
    public PyramidDraftTypeHandler(final GameCoordinationWorker gameWorker) {
        this.gameWorker = gameWorker;
    }

    @Override
    public String draftType() {
        return "pyramid";
    }

    @Override
    public int defaultMinPlayers() {
        return 2;
    }

    @Override
    public int defaultMaxPlayers() {
        return 2;
    }

    @Override
    public Uni<String> startGame(final String cubeID,
                                 final List<LobbyPlayer> players,
                                 final Map<String, Object> config) {
        int doubleDraftPicks = intConfig(config, "numberOfDoubleDraftPicksPerPlayer", 0);

        List<PlayerCreationInfo> playerInfo = players.stream()
                .map(p -> new PlayerCreationInfo(
                        p.getDisplayName(),
                        p.getAccountID() != null ? p.getAccountID() : "guest-" + p.getPlayerToken()))
                .toList();

        GameCreationInfo creationInfo = new GameCreationInfo(cubeID, playerInfo, doubleDraftPicks);

        return gameWorker.startGame(creationInfo)
                .map(game -> game.getGameID());
    }

    private static int intConfig(final Map<String, Object> config, final String key, final int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }
}
