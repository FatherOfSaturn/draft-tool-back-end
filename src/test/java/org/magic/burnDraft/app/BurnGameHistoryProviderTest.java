package org.magic.burnDraft.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.DraftGameState;
import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.api.GameHistoryPlayer;
import org.magic.burnDraft.api.BurnGameInfo;
import org.magic.burnDraft.api.BurnPlayer;
import org.magic.burnDraft.api.BurnRemainderPolicy;
import org.magic.burnDraft.app.GameCoordination.BurnDraftDbHandler;
import org.magic.common.card.Card;
import org.magic.common.card.CardDetails;
import org.magic.common.card.CardPack;
import org.magic.common.game.DraftDirection;
import org.magic.common.game.GameState;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class BurnGameHistoryProviderTest {

    @Mock
    BurnDraftDbHandler dbHandler;

    @Test
    void shouldMapInProgressGameWithDraftedAndBurnedProgress() {
        BurnPlayer alice = player("Alice", "acc-1", 0, 2, 1, 2);
        BurnPlayer bob = player("Bob", "acc-2", 1, 2, 6, 6);
        BurnGameInfo game = game(List.of(alice, bob), GameState.GAME_STARTED, 0);

        when(dbHandler.findGamesByAccountID("acc-1")).thenReturn(Uni.createFrom().item(List.of(game)));

        BurnGameHistoryProvider provider = new BurnGameHistoryProvider(dbHandler);
        List<GameHistoryEntry> result = provider.findGamesByAccountID("acc-1").await().indefinitely();

        assertEquals(1, result.size());
        GameHistoryEntry entry = result.get(0);
        assertEquals("gid", entry.gameID());
        assertEquals("burn", entry.gameType());
        assertEquals(DraftGameState.GAME_IN_PROGRESS, entry.gameState());

        GameHistoryPlayer alicePlayer = entry.players().get(0);
        assertEquals("Alice", alicePlayer.name());
        assertEquals("acc-1", alicePlayer.accountID());
        assertEquals(1, alicePlayer.currentPack());
        assertEquals(2, alicePlayer.totalPacks());
        assertEquals(12 - 1 - 2, alicePlayer.cardsLeftToDraft());
        assertEquals(0, alicePlayer.draftOrderNumber());
        assertFalse(alicePlayer.doneDrafting());

        GameHistoryPlayer bobPlayer = entry.players().get(1);
        assertEquals(0, bobPlayer.cardsLeftToDraft());
        assertTrue(bobPlayer.doneDrafting());
    }

    @Test
    void shouldMapCompleteGameToCompleteState() {
        BurnPlayer alice = player("Alice", "acc-1", 0, 2, 6, 6);
        BurnGameInfo game = game(List.of(alice, player("Bob", "acc-2", 1, 2, 6, 6)),
                GameState.GAME_COMPLETE, 2);

        when(dbHandler.findGamesByAccountID("acc-1")).thenReturn(Uni.createFrom().item(List.of(game)));

        BurnGameHistoryProvider provider = new BurnGameHistoryProvider(dbHandler);
        GameHistoryEntry entry = provider.findGamesByAccountID("acc-1").await().indefinitely().get(0);

        assertEquals(DraftGameState.GAME_COMPLETE, entry.gameState());
        assertEquals(3, entry.players().get(0).currentPack());
        assertEquals(0, entry.players().get(0).cardsLeftToDraft());
        assertTrue(entry.players().get(0).doneDrafting());
    }

    @Test
    void shouldReturnOnlyTheRequestedAccountsGames() {
        BurnGameInfo game = game(List.of(player("Alice", "acc-1", 0, 1, 0, 0),
                player("Bob", "acc-2", 1, 1, 0, 0)), GameState.GAME_STARTED, 0);
        when(dbHandler.findGamesByAccountID("acc-99")).thenReturn(Uni.createFrom().item(List.of()));

        BurnGameHistoryProvider provider = new BurnGameHistoryProvider(dbHandler);

        assertTrue(provider.findGamesByAccountID("acc-99").await().indefinitely().isEmpty());
        assertNull(game.getPlayers().stream().filter(p -> "acc-99".equals(p.getAccountID()))
                .findFirst().orElse(null));
    }

    private BurnPlayer player(final String name, final String accountID, final int draftOrderNumber,
                              final int packsDealt, final int cardsDrafted, final int cardsBurned) {
        CardPack pack = new CardPack(0, List.<Card>of(card("x")), 6, false);
        List<CardPack> dealtPacks = java.util.Collections.nCopies(packsDealt, pack);
        List<Card> drafted = java.util.Collections.nCopies(cardsDrafted, card("d"));
        List<Card> burned = java.util.Collections.nCopies(cardsBurned, card("b"));
        return new BurnPlayer(name, accountID, draftOrderNumber, dealtPacks, List.of(), drafted, burned);
    }

    private static Card card(final String id) {
        CardDetails details = new CardDetails("set", "Set", "sf-" + id, "s.jpg", "n.jpg", null,
                "Card " + id, "Instant", 1, List.of("r"));
        return new Card("Card " + id, details, id, 1, "Instant");
    }

    private BurnGameInfo game(final List<BurnPlayer> players, final GameState state, final int currentPackIndex) {
        return new BurnGameInfo("gid", "cube-1", "burn", players, state, Instant.now(),
                currentPackIndex, DraftDirection.ASCENDING, 1, 1, BurnRemainderPolicy.BURN_EXTRAS);
    }
}
