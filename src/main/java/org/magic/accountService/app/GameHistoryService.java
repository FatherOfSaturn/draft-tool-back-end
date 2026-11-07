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

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * Aggregates game history across all draft types for an account. Collects entries
 * from every discovered {@link GameHistoryProvider} concurrently, resolves display
 * names from the Accounts collection, and returns the union sorted newest first.
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
     * Fetches the full game history for the authenticated user (resolved from the
     * Google {@code sub} to their account ID) across all draft types.
     *
     * @param googleSub the authenticated user's Google subject
     * @return the aggregated entries, sorted by creation time descending
     */
    public Uni<List<GameHistoryEntry>> getGameHistory(final String googleSub) {
        return accountDbHandler.findByGoogleSub(googleSub)
                .chain(account -> account
                        .map(found -> getGameHistoryByAccountID(found.getAccountID()))
                        .orElseGet(() -> Uni.createFrom().item(List.of())));
    }

    /**
     * Fetches the full game history for an arbitrary account, bypassing the
     * caller-identity check. Admin-only callers.
     *
     * @param accountID the account to look up
     * @return the aggregated entries, sorted by creation time descending
     */
    public Uni<List<GameHistoryEntry>> getGameHistoryByAccountID(final String accountID) {
        List<Uni<List<GameHistoryEntry>>> lookups = new ArrayList<>();
        providers.stream().forEach(provider -> lookups.add(provider.findGamesByAccountID(accountID)));
        if (lookups.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return Uni.combine().all().unis(lookups)
                .with(GameHistoryService::flattenResults)
                .chain(entries -> resolveDisplayNames(entries)
                        .map(displayNames -> entries.stream()
                                .map(entry -> withDisplayNames(entry, displayNames))
                                .sorted(Comparator.comparing(GameHistoryEntry::createdAt,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                                .toList()))
                .invoke(entries -> LOGGER.info("Resolved {} history entries for account {}",
                        entries.size(), accountID));
    }

    @SuppressWarnings("unchecked")
    private static List<GameHistoryEntry> flattenResults(final List<?> lists) {
        List<GameHistoryEntry> entries = new ArrayList<>();
        for (Object list : lists) {
            entries.addAll((List<GameHistoryEntry>) list);
        }
        return entries;
    }

    private Uni<Map<String, String>> resolveDisplayNames(final List<GameHistoryEntry> entries) {
        List<String> accountIDs = entries.stream()
                .flatMap(entry -> entry.players().stream())
                .map(GameHistoryPlayer::accountID)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        if (accountIDs.isEmpty()) {
            return Uni.createFrom().item(Map.of());
        }

        return Uni.combine().all().unis(accountIDs.stream()
                        .map(id -> accountDbHandler.findByIdOptional(id)
                                .map(account -> account.map(Account::getDisplayName)
                                        .filter(name -> name != null)
                                        .map(name -> Map.of(id, name))
                                        .orElseGet(Map::of)))
                        .toList())
                .with(GameHistoryService::mergeDisplayNameMaps);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> mergeDisplayNameMaps(final List<?> results) {
        Map<String, String> displayNames = new HashMap<>();
        for (Object result : results) {
            displayNames.putAll((Map<String, String>) result);
        }
        return displayNames;
    }

    private static GameHistoryEntry withDisplayNames(final GameHistoryEntry entry,
                                                     final Map<String, String> displayNames) {
        return new GameHistoryEntry(
                entry.gameID(),
                entry.cubeID(),
                entry.gameType(),
                entry.gameState(),
                entry.players().stream()
                        .map(player -> withDisplayName(player, displayNames))
                        .toList(),
                entry.createdAt());
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
