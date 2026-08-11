package org.magic.accountService.app;

import java.util.List;

import org.magic.accountService.api.GameHistoryEntry;

/**
 * Strategy interface for collecting an account's games from a specific draft
 * format. Implementations are CDI beans discovered via {@code Instance<GameHistoryProvider>},
 * so adding a new draft type only requires adding a new bean.
 */
public interface GameHistoryProvider {

    /**
     * Finds all games of this provider's draft type that the given account
     * participates in, mapped to the unified history shape.
     *
     * @param accountID the account to look up games for
     * @return the matching game history entries, newest first
     */
    List<GameHistoryEntry> findGamesByAccountID(String accountID);
}
