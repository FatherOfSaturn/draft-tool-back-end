package org.magic.pyramidDraft.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.DraftGameState;
import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.api.GameHistoryPlayer;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.api.Player;
import org.magic.pyramidDraft.api.card.CardPack;
import org.magic.pyramidDraft.app.GameCoordination.PyramidDraftDbHandler;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PyramidGameHistoryProviderTest {

    @Mock
    PyramidDraftDbHandler dbHandler;

    @Test
    void shouldMapStartedGameToInProgressWithPhase1Totals() {
        Player alice = player("Alice", "acc-1", 3, 20, 13, false);
        Player bob = player("Bob", "acc-2", 5, 20, 13, true);
        GameInfo game = new GameInfo("gid", "cid", "pyramid", List.of(alice, bob), GameState.GAME_STARTED, Instant.now());

        when(dbHandler.findGamesByAccountID("acc-1")).thenReturn(List.of(game));

        PyramidGameHistoryProvider provider = new PyramidGameHistoryProvider(dbHandler);

        List<GameHistoryEntry> result = provider.findGamesByAccountID("acc-1");

        assertEquals(1, result.size());
        GameHistoryEntry entry = result.get(0);
        assertEquals("gid", entry.gameID());
        assertEquals("pyramid", entry.gameType());
        assertEquals(DraftGameState.GAME_IN_PROGRESS, entry.gameState());

        GameHistoryPlayer alicePlayer = entry.players().get(0);
        assertEquals("Alice", alicePlayer.name());
        assertEquals("acc-1", alicePlayer.accountID());
        assertEquals(3, alicePlayer.currentPack());
        assertEquals(20, alicePlayer.totalPacks());
        assertFalse(alicePlayer.doneDrafting());
        assertNull(alicePlayer.cardsLeftToDraft());
        assertNull(alicePlayer.draftOrderNumber());

        assertTrue(entry.players().get(1).doneDrafting());
    }

    @Test
    void shouldMapMergedGameToMergedStateWithPhase2Totals() {
        Player alice = player("Alice", "acc-1", 0, 20, 13, false);
        GameInfo game = new GameInfo("gid", "cid", "pyramid", List.of(alice, player("Bob", "acc-2", 0, 20, 13, false)), GameState.GAME_MERGED, Instant.now());

        when(dbHandler.findGamesByAccountID("acc-1")).thenReturn(List.of(game));

        PyramidGameHistoryProvider provider = new PyramidGameHistoryProvider(dbHandler);

        GameHistoryEntry entry = provider.findGamesByAccountID("acc-1").get(0);

        assertEquals(DraftGameState.GAME_MERGED, entry.gameState());
        assertEquals(13, entry.players().get(0).totalPacks());
    }

    @Test
    void shouldMarkDoneWhenGameCompleteEvenIfPlayerNotReady() {
        Player alice = player("Alice", "acc-1", 13, 20, 13, false);
        GameInfo game = new GameInfo("gid", "cid", "pyramid", List.of(alice, player("Bob", "acc-2", 13, 20, 13, false)), GameState.GAME_COMPLETE, Instant.now());

        when(dbHandler.findGamesByAccountID("acc-1")).thenReturn(List.of(game));

        PyramidGameHistoryProvider provider = new PyramidGameHistoryProvider(dbHandler);

        GameHistoryEntry entry = provider.findGamesByAccountID("acc-1").get(0);

        assertEquals(DraftGameState.GAME_COMPLETE, entry.gameState());
        assertTrue(entry.players().get(0).doneDrafting());
        assertEquals(13, entry.players().get(0).totalPacks());
    }

    private Player player(final String name, final String accountID, final int currentDraftPack,
                          final int phase1TotalPacks, final int phase2TotalPacks, final boolean readyForMerge) {
        return new Player(name, accountID, List.<CardPack>of(), 0, null, currentDraftPack, readyForMerge,
                phase1TotalPacks, phase2TotalPacks);
    }
}
