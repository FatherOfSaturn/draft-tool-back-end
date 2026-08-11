package org.magic.accountService.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.Account;
import org.magic.accountService.api.DraftGameState;
import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.api.GameHistoryPlayer;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.enterprise.inject.Instance;

@ExtendWith(MockitoExtension.class)
class GameHistoryServiceTest {

    @Mock
    AccountDbHandler accountDbHandler;

    @Mock
    GameHistoryProvider pyramidProvider;

    @Mock
    GameHistoryProvider classicProvider;

    @Test
    void shouldAggregateHistoryFromAllProvidersSortedNewestFirst() {
        var older = entry("game-1", "pyramid", Instant.parse("2024-01-01T00:00:00Z"), player("Alice", "acc-1"));
        var newer = entry("game-2", "classic", Instant.parse("2024-06-01T00:00:00Z"), player("Alice", "acc-1"));

        when(pyramidProvider.findGamesByAccountID("acc-1")).thenReturn(List.of(older));
        when(classicProvider.findGamesByAccountID("acc-1")).thenReturn(List.of(newer));

        GameHistoryService service = new GameHistoryService(instanceOf(pyramidProvider, classicProvider), accountDbHandler);

        List<GameHistoryEntry> result = service.getGameHistory("acc-1");

        assertEquals(2, result.size());
        assertEquals("game-2", result.get(0).gameID());
        assertEquals("game-1", result.get(1).gameID());
    }

    @Test
    void shouldResolveDisplayNameFromAccountCollection() {
        var account = new Account();
        account.setAccountID("acc-1");
        account.setDisplayName("Alice Smith");

        when(pyramidProvider.findGamesByAccountID("acc-1"))
                .thenReturn(List.of(entry("game-1", "pyramid", Instant.parse("2024-01-01T00:00:00Z"),
                        player("Alice", "acc-1"), player("Bob", "acc-2"))));
        when(accountDbHandler.findById("acc-1")).thenReturn(Optional.of(account));
        when(accountDbHandler.findById("acc-2")).thenReturn(Optional.empty());

        GameHistoryService service = new GameHistoryService(instanceOf(pyramidProvider), accountDbHandler);

        List<GameHistoryEntry> result = service.getGameHistory("acc-1");

        GameHistoryPlayer alice = result.get(0).players().get(0);
        GameHistoryPlayer bob = result.get(0).players().get(1);
        assertEquals("Alice Smith", alice.displayName());
        assertNull(bob.displayName());
    }

    @Test
    void shouldReturnEmptyWhenNoProviderHasGames() {
        when(pyramidProvider.findGamesByAccountID("acc-1")).thenReturn(List.of());
        when(classicProvider.findGamesByAccountID("acc-1")).thenReturn(List.of());

        GameHistoryService service = new GameHistoryService(instanceOf(pyramidProvider, classicProvider), accountDbHandler);

        List<GameHistoryEntry> result = service.getGameHistory("acc-1");

        assertTrue(result.isEmpty());
    }

    private GameHistoryPlayer player(final String name, final String accountID) {
        return new GameHistoryPlayer(name, null, accountID, 1, 3, false, null, null);
    }

    private GameHistoryEntry entry(final String gameID, final String gameType, final Instant createdAt,
                                   final GameHistoryPlayer... players) {
        return new GameHistoryEntry(gameID, "cube-1", gameType, DraftGameState.GAME_IN_PROGRESS,
                List.of(players), createdAt);
    }

    @SuppressWarnings("unchecked")
    private Instance<GameHistoryProvider> instanceOf(final GameHistoryProvider... providers) {
        Instance<GameHistoryProvider> instance = mock(Instance.class);
        when(instance.stream()).thenReturn(List.of(providers).stream());
        return instance;
    }
}
