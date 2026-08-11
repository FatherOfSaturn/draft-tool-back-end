package org.magic.accountService.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.accountService.api.Account;
import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.api.GameHistoryPlayer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Aggregates game history across all draft types for an account. Collects entries
 * from every discovered {@link GameHistoryProvider}, resolves display names from
 * the Accounts collection, and returns the union sorted newest first.
 */
@ApplicationScoped
public class GameHistoryService {
    private static final Logger LOGGER = LogManager.getLogger(GameHistoryService.class);

    private final Instance<GameHistoryProvider> providers;
    private final AccountDbHandler accountDbHandler;

    @Inject
    public GameHistoryService(final Instance<GameHistoryProvider> providers,
                              final AccountDbHandler accountDbHandler) {
        this.providers = providers;
        this.accountDbHandler = accountDbHandler;
    }

    /**
     * Fetches the full game history for an account across all draft types.
     *
     * @param accountID the account to look up games for
     * @return the aggregated entries, sorted by creation time descending
     */
    public List<GameHistoryEntry> getGameHistory(final String accountID) {
        List<GameHistoryEntry> entries = new ArrayList<>();
        providers.stream()
                .forEach(provider -> entries.addAll(provider.findGamesByAccountID(accountID)));

        entries.sort(Comparator.comparing(GameHistoryEntry::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, String> displayNames = resolveDisplayNames(entries);

        LOGGER.info("Resolved {} history entries for account {}", entries.size(), accountID);

        return entries.stream()
                .map(entry -> new GameHistoryEntry(
                        entry.gameID(),
                        entry.cubeID(),
                        entry.gameType(),
                        entry.gameState(),
                        entry.players().stream()
                                .map(player -> withDisplayName(player, displayNames))
                                .toList(),
                        entry.createdAt()))
                .toList();
    }

    private Map<String, String> resolveDisplayNames(final List<GameHistoryEntry> entries) {
        Map<String, String> displayNames = new HashMap<>();
        entries.stream()
                .flatMap(entry -> entry.players().stream())
                .map(GameHistoryPlayer::accountID)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .forEach(id -> accountDbHandler.findById(id)
                        .map(Account::getDisplayName)
                        .filter(name -> name != null)
                        .ifPresent(name -> displayNames.put(id, name)));
        return displayNames;
    }

    private static GameHistoryPlayer withDisplayName(final GameHistoryPlayer player,
                                                     final Map<String, String> displayNames) {
        String displayName = player.displayName();
        if (displayName == null && player.accountID() != null) {
            displayName = displayNames.get(player.accountID());
        }
        return new GameHistoryPlayer(
                player.name(),
                displayName,
                player.accountID(),
                player.currentPack(),
                player.totalPacks(),
                player.doneDrafting(),
                player.cardsLeftToDraft(),
                player.draftOrderNumber());
    }
}
