package org.magic.accountService.app;

import java.util.List;

import org.magic.accountService.api.GameHistoryEntry;

/**
 * Template for {@link GameHistoryProvider} implementations: resolves a
 * format-specific list of games for an account and maps each game into the
 * unified {@link GameHistoryEntry} shape.
 *
 * @param <INFO> the draft-format-specific persisted game type
 */
public abstract class AbstractGameHistoryProvider<INFO> implements GameHistoryProvider {

    /**
     * Loads all games of this format the account participated in.
     *
     * @param accountID the account to look up
     * @return the format-specific games
     */
    protected abstract List<INFO> findGamesByAccount(String accountID);

    /**
     * Maps one format-specific game into the unified history shape.
     *
     * @param game the game to map
     * @return the unified history entry
     */
    protected abstract GameHistoryEntry toEntry(INFO game);

    @Override
    public List<GameHistoryEntry> findGamesByAccountID(final String accountID) {
        return findGamesByAccount(accountID).stream()
                .map(this::toEntry)
                .toList();
    }
}
