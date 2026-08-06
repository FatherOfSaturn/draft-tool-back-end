package org.magic.pyramidDraft.app;

import java.util.List;

import org.magic.accountService.api.DraftGameState;
import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.api.GameHistoryPlayer;
import org.magic.accountService.app.GameHistoryProvider;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.api.Player;
import org.magic.pyramidDraft.app.GameCoordination.PyramidDraftDbHandler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Maps pyramid draft games into the unified game history shape. Reports the
 * player's progress within the current phase using the persisted phase pack
 * totals, since phase-1 pack counts are not recoverable after the merge.
 */
@ApplicationScoped
public class PyramidGameHistoryProvider implements GameHistoryProvider {

    private final PyramidDraftDbHandler dbHandler;

    @Inject
    public PyramidGameHistoryProvider(final PyramidDraftDbHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    @Override
    public List<GameHistoryEntry> findGamesByAccountID(final String accountID) {
        return dbHandler.findGamesByAccountID(accountID).stream()
                .map(this::toEntry)
                .toList();
    }

    private GameHistoryEntry toEntry(final GameInfo game) {
        boolean mergedPhase = game.getGameState() == GameState.GAME_MERGED
                || game.getGameState() == GameState.GAME_COMPLETE;

        List<GameHistoryPlayer> players = game.getPlayers().stream()
                .map(player -> {
                    int totalPacks = mergedPhase
                            ? player.getPhase2TotalPacks()
                            : player.getPhase1TotalPacks();
                    boolean doneDrafting = game.getGameState() == GameState.GAME_COMPLETE
                            || player.isReadyForMerge();
                    return new GameHistoryPlayer(
                            player.getPlayerName(),
                            null,
                            player.getAccountID(),
                            player.getCurrentDraftPack(),
                            totalPacks,
                            doneDrafting,
                            null,
                            null);
                })
                .toList();

        return new GameHistoryEntry(
                game.getGameID(),
                game.getCubeID(),
                "pyramid",
                toDraftGameState(game.getGameState()),
                players,
                game.getCreatedAt());
    }

    private static DraftGameState toDraftGameState(final GameState state) {
        return switch (state) {
            case GAME_STARTED -> DraftGameState.GAME_IN_PROGRESS;
            case GAME_MERGED -> DraftGameState.GAME_MERGED;
            case GAME_COMPLETE -> DraftGameState.GAME_COMPLETE;
        };
    }
}
