package org.magic.classicDraft.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.DraftGameState;
import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.api.GameHistoryPlayer;
import org.magic.classicDraft.api.ClassicGameInfo;
import org.magic.classicDraft.api.ClassicGameState;
import org.magic.classicDraft.api.ClassicPlayer;
import org.magic.classicDraft.api.DraftDirection;
import org.magic.pyramidDraft.api.card.CardPack;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.classicDraft.app.GameCoordination.ClassicDraftDbHandler;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassicGameHistoryProviderTest {

    @Mock
    ClassicDraftDbHandler dbHandler;

    @Test
    void shouldMapStartedGameToInProgressWithCardProgress() {
        ClassicPlayer alice = classicPlayer("Alice", "acc-1", 0, 2, 1);
        ClassicPlayer bob = classicPlayer("Bob", "acc-2", 1, 2, 6);
        ClassicGameInfo game = game(List.of(alice, bob), ClassicGameState.GAME_STARTED, 0);

        when(dbHandler.findGamesByAccountID("acc-1")).thenReturn(List.of(game));

        ClassicGameHistoryProvider provider = new ClassicGameHistoryProvider(dbHandler);

        List<GameHistoryEntry> result = provider.findGamesByAccountID("acc-1");

        assertEquals(1, result.size());
        GameHistoryEntry entry = result.get(0);
        assertEquals("gid", entry.gameID());
        assertEquals("classic", entry.gameType());
        assertEquals(DraftGameState.GAME_IN_PROGRESS, entry.gameState());

        GameHistoryPlayer alicePlayer = entry.players().get(0);
        assertEquals("Alice", alicePlayer.name());
        assertEquals("acc-1", alicePlayer.accountID());
        assertEquals(1, alicePlayer.currentPack());
        assertEquals(2, alicePlayer.totalPacks());
        assertEquals(5, alicePlayer.cardsLeftToDraft());
        assertEquals(0, alicePlayer.draftOrderNumber());
        assertFalse(alicePlayer.doneDrafting());

        GameHistoryPlayer bobPlayer = entry.players().get(1);
        assertEquals(0, bobPlayer.cardsLeftToDraft());
        assertTrue(bobPlayer.doneDrafting());
    }

    @Test
    void shouldMapCompleteGameToCompleteState() {
        ClassicPlayer alice = classicPlayer("Alice", "acc-1", 0, 2, 6);
        ClassicGameInfo game = game(List.of(alice, classicPlayer("Bob", "acc-2", 1, 2, 6)), ClassicGameState.GAME_COMPLETE, 1);

        when(dbHandler.findGamesByAccountID("acc-1")).thenReturn(List.of(game));

        ClassicGameHistoryProvider provider = new ClassicGameHistoryProvider(dbHandler);

        GameHistoryEntry entry = provider.findGamesByAccountID("acc-1").get(0);

        assertEquals(DraftGameState.GAME_COMPLETE, entry.gameState());
        assertEquals(2, entry.players().get(0).currentPack());
        assertEquals(0, entry.players().get(0).cardsLeftToDraft());
        assertTrue(entry.players().get(0).doneDrafting());
    }

    private ClassicPlayer classicPlayer(final String name, final String accountID, final int draftOrderNumber,
                                        final int packsDealt, final int cardsDraftedCount) {
        CardPack pack = new CardPack(0, List.<Card>of(), 3, false);
        List<CardPack> dealtPacks = java.util.Collections.nCopies(packsDealt, pack);
        List<Card> cardsDrafted = java.util.Collections.nCopies(cardsDraftedCount, null);
        return new ClassicPlayer(name, accountID, draftOrderNumber, dealtPacks, List.of(), cardsDrafted);
    }

    private ClassicGameInfo game(final List<ClassicPlayer> players, final ClassicGameState state, final int currentPackIndex) {
        return new ClassicGameInfo("gid", "cid", "classic", players, state, Instant.now(), currentPackIndex,
                DraftDirection.ASCENDING);
    }
}
