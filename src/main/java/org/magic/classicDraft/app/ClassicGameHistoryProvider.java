package org.magic.classicDraft.app;

import java.util.List;

import org.magic.accountService.api.DraftGameState;
import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.api.GameHistoryPlayer;
import org.magic.accountService.app.GameHistoryProvider;
import org.magic.classicDraft.api.ClassicGameInfo;
import org.magic.classicDraft.api.ClassicGameState;
import org.magic.classicDraft.api.ClassicPlayer;
import org.magic.classicDraft.app.GameCoordination.ClassicDraftDbHandler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Maps classic draft games into the unified game history shape. Reports pack
 * progress as the 1-based pack index, and per-player cards remaining to draft.
 */
@ApplicationScoped
public class ClassicGameHistoryProvider implements GameHistoryProvider {

    private final ClassicDraftDbHandler dbHandler;

    @Inject
    public ClassicGameHistoryProvider(final ClassicDraftDbHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    @Override
    public List<GameHistoryEntry> findGamesByAccountID(final String accountID) {
        return dbHandler.findGamesByAccountID(accountID).stream()
                .map(this::toEntry)
                .toList();
    }

    private GameHistoryEntry toEntry(final ClassicGameInfo game) {
        List<GameHistoryPlayer> players = game.getPlayers().stream()
                .map(player -> {
                    int cardsLeftToDraft = cardsLeftToDraft(player);
                    return new GameHistoryPlayer(
                            player.getPlayerName(),
                            null,
                            player.getAccountID(),
                            game.getCurrentPackIndex() + 1,
                            player.getDealtCardPacks().size(),
                            cardsLeftToDraft <= 0,
                            cardsLeftToDraft,
                            player.getDraftOrderNumber());
                })
                .toList();

        return new GameHistoryEntry(
                game.getGameID(),
                game.getCubeID(),
                "classic",
                game.getGameState() == ClassicGameState.GAME_COMPLETE
                        ? DraftGameState.GAME_COMPLETE
                        : DraftGameState.GAME_IN_PROGRESS,
                players,
                game.getCreatedAt());
    }

    private static int cardsLeftToDraft(final ClassicPlayer player) {
        return player.getDealtCardPacks().size()
                * (player.getDealtCardPacks().isEmpty()
                        ? 0
                        : player.getDealtCardPacks().get(0).getOriginalCardsInPack())
                - player.getCardsDrafted().size();
    }
}
