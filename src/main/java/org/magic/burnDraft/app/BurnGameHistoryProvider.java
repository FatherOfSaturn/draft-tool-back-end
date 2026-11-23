package org.magic.burnDraft.app;

import java.util.List;

import org.magic.accountService.api.DraftGameState;
import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.api.GameHistoryPlayer;
import org.magic.accountService.app.AbstractGameHistoryProvider;
import org.magic.burnDraft.api.BurnGameInfo;
import org.magic.burnDraft.api.BurnPlayer;
import org.magic.burnDraft.app.GameCoordination.BurnDraftDbHandler;
import org.magic.common.game.GameState;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Maps burn draft games into the unified game history shape, mirroring the
 * classic provider. Reports pack progress as the 1-based pack index and the
 * cards still available (neither drafted nor burned).
 */
@ApplicationScoped
public class BurnGameHistoryProvider extends AbstractGameHistoryProvider<BurnGameInfo> {

    private final BurnDraftDbHandler dbHandler;

    @Inject
    public BurnGameHistoryProvider(final BurnDraftDbHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    @Override
    protected Uni<List<BurnGameInfo>> findGamesByAccount(final String accountID) {
        return dbHandler.findGamesByAccountID(accountID);
    }

    @Override
    protected GameHistoryEntry toEntry(final BurnGameInfo game) {
        List<GameHistoryPlayer> players = game.getPlayers().stream()
                .map(player -> {
                    int cardsLeftToDraft = cardsLeft(player);
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
                "burn",
                game.getGameState() == GameState.GAME_COMPLETE
                        ? DraftGameState.GAME_COMPLETE
                        : DraftGameState.GAME_IN_PROGRESS,
                players,
                game.getCreatedAt());
    }

    private static int cardsLeft(final BurnPlayer player) {
        return player.getDealtCardPacks().size()
                * (player.getDealtCardPacks().isEmpty()
                        ? 0
                        : player.getDealtCardPacks().get(0).getOriginalCardsInPack())
                - player.getCardsDrafted().size()
                - player.getCardsBurned().size();
    }
}
