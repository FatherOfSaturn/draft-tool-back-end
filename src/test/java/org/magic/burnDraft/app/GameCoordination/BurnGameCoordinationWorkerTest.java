package org.magic.burnDraft.app.GameCoordination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.burnDraft.api.BurnGameCreationInfo;
import org.magic.burnDraft.api.BurnGameInfo;
import org.magic.burnDraft.api.BurnPlayer;
import org.magic.burnDraft.api.BurnPlayerCreationInfo;
import org.magic.burnDraft.api.BurnPlayerSnapshot;
import org.magic.burnDraft.api.BurnRemainderPolicy;
import org.magic.burnDraft.api.BurnTurnRequest;
import org.magic.burnDraft.api.PlayerBurnData;
import org.magic.common.card.Card;
import org.magic.common.card.CardDetails;
import org.magic.common.card.CardPack;
import org.magic.common.card.Cube;
import org.magic.common.cube.CubeDownloader;
import org.magic.common.game.DraftDirection;
import org.magic.common.game.GameState;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.BadRequestException;

@ExtendWith(MockitoExtension.class)
class BurnGameCoordinationWorkerTest {

    @Mock
    BurnDraftDbHandler dbHandler;

    @Mock
    CubeDownloader cubeDownloader;

    BurnGameCoordinationWorker worker;

    @BeforeEach
    void setup() {
        worker = new BurnGameCoordinationWorker(dbHandler, cubeDownloader);
    }

    // --- turn rules ---------------------------------------------------------

    @Test
    void expectedTurnCountsOnFullPass() {
        assertEquals(new BurnGameCoordinationWorker.TurnCounts(1, 1),
                BurnGameCoordinationWorker.expectedTurnCounts(4, 1, 1, BurnRemainderPolicy.BURN_EXTRAS));
        assertEquals(new BurnGameCoordinationWorker.TurnCounts(2, 2),
                BurnGameCoordinationWorker.expectedTurnCounts(6, 2, 2, BurnRemainderPolicy.DRAFT_EXTRAS));
    }

    @Test
    void expectedTurnCountsOnPartialPassBurnExtras() {
        // 3 cards, draft 2, burn 2 -> draft the quota, burn the single extra
        assertEquals(new BurnGameCoordinationWorker.TurnCounts(2, 1),
                BurnGameCoordinationWorker.expectedTurnCounts(3, 2, 2, BurnRemainderPolicy.BURN_EXTRAS));
        // fewer cards than the draft quota -> draft them all, nothing to burn
        assertEquals(new BurnGameCoordinationWorker.TurnCounts(1, 0),
                BurnGameCoordinationWorker.expectedTurnCounts(1, 2, 2, BurnRemainderPolicy.BURN_EXTRAS));
        // exactly the draft quota, no extras
        assertEquals(new BurnGameCoordinationWorker.TurnCounts(2, 0),
                BurnGameCoordinationWorker.expectedTurnCounts(2, 2, 2, BurnRemainderPolicy.BURN_EXTRAS));
    }

    @Test
    void expectedTurnCountsOnPartialPassDraftExtras() {
        assertEquals(new BurnGameCoordinationWorker.TurnCounts(3, 0),
                BurnGameCoordinationWorker.expectedTurnCounts(3, 2, 2, BurnRemainderPolicy.DRAFT_EXTRAS));
        assertEquals(new BurnGameCoordinationWorker.TurnCounts(1, 0),
                BurnGameCoordinationWorker.expectedTurnCounts(1, 2, 2, BurnRemainderPolicy.DRAFT_EXTRAS));
    }

    // --- turns --------------------------------------------------------------

    @Test
    void fullPassDraftsBurnsAndPassesThePack() {
        Card a = card("a");
        Card b = card("b");
        Card c = card("c");
        Card d = card("d");
        CardPack active = new CardPack(0, List.of(a, b, c, d), 4, false);
        BurnPlayer p0 = player("Alice", 0, "tok-0", List.of(active), List.of(copy(active)));
        BurnPlayer p1 = player("Bob", 1, "tok-1", List.of(pack(1, "x")), List.of());
        BurnGameInfo game = game(List.of(p0, p1), 1, 1, BurnRemainderPolicy.BURN_EXTRAS);
        stubGame(game);

        List<Card> drafted = worker.takeTurn("tok-0", "gid", turn(List.of("a"), List.of("b")))
                .await().indefinitely();

        assertEquals(1, drafted.size());
        assertEquals("a", drafted.get(0).getCardID());
        assertEquals(1, p0.getCardsDrafted().size());
        assertEquals(1, p0.getCardsBurned().size());
        assertEquals("b", p0.getCardsBurned().get(0).getCardID());
        // remaining pack (c, d) was passed to the next player
        assertTrue(p0.getActiveCardPacks().isEmpty());
        assertEquals(1, p1.getActiveCardPacks().size());
        assertEquals(2, p1.getActiveCardPacks().get(0).getCardsInPack().size());
        verify(dbHandler).updateGame(game);
    }

    @Test
    void partialPassBurnsExtras() {
        CardPack active = new CardPack(0, List.of(card("a"), card("b"), card("c")), 3, false);
        BurnPlayer p0 = player("Alice", 0, "tok-0", List.of(active), List.of(copy(active)));
        BurnPlayer p1 = player("Bob", 1, "tok-1", List.of(pack(1, "x")), List.of());
        BurnGameInfo game = game(List.of(p0, p1), 2, 2, BurnRemainderPolicy.BURN_EXTRAS);
        stubGame(game);

        List<Card> drafted = worker.takeTurn("tok-0", "gid", turn(List.of("a", "b"), List.of("c")))
                .await().indefinitely();

        assertEquals(2, drafted.size());
        assertEquals(2, p0.getCardsDrafted().size());
        assertEquals(1, p0.getCardsBurned().size());
        assertEquals("c", p0.getCardsBurned().get(0).getCardID());
        assertTrue(p1.getActiveCardPacks().isEmpty(), "consumed packs are not passed on");
    }

    @Test
    void partialPassDraftsExtras() {
        CardPack active = new CardPack(0, List.of(card("a"), card("b"), card("c")), 3, false);
        BurnPlayer p0 = player("Alice", 0, "tok-0", List.of(active), List.of(copy(active)));
        BurnPlayer p1 = player("Bob", 1, "tok-1", List.of(pack(1, "x")), List.of());
        BurnGameInfo game = game(List.of(p0, p1), 2, 2, BurnRemainderPolicy.DRAFT_EXTRAS);
        stubGame(game);

        List<Card> drafted = worker.takeTurn("tok-0", "gid", turn(List.of("a", "b", "c"), List.of()))
                .await().indefinitely();

        assertEquals(3, drafted.size());
        assertEquals(3, p0.getCardsDrafted().size());
        assertTrue(p0.getCardsBurned().isEmpty());
        assertTrue(p1.getActiveCardPacks().isEmpty());
    }

    @Test
    void rejectsWrongCardCounts() {
        CardPack active = new CardPack(0, List.of(card("a"), card("b"), card("c")), 3, false);
        BurnPlayer p0 = player("Alice", 0, "tok-0", List.of(active), List.of(copy(active)));
        BurnGameInfo game = game(List.of(p0, player("Bob", 1, "tok-1", List.of(pack(1, "x")), List.of())),
                2, 2, BurnRemainderPolicy.BURN_EXTRAS);
        stubGame(game);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                worker.takeTurn("tok-0", "gid", turn(List.of("a"), List.of("b"))).await().indefinitely());
        assertTrue(ex.getMessage().contains("expected to draft 2 and burn 1"), ex.getMessage());
    }

    @Test
    void rejectsCardNotInPack() {
        CardPack active = new CardPack(0, List.of(card("a"), card("b"), card("c"), card("d")), 4, false);
        BurnPlayer p0 = player("Alice", 0, "tok-0", List.of(active), List.of(copy(active)));
        BurnGameInfo game = game(List.of(p0, player("Bob", 1, "tok-1", List.of(pack(1, "x")), List.of())),
                1, 1, BurnRemainderPolicy.BURN_EXTRAS);
        stubGame(game);

        assertThrows(BadRequestException.class, () ->
                worker.takeTurn("tok-0", "gid", turn(List.of("nope"), List.of("b"))).await().indefinitely());
    }

    @Test
    void rejectsOverlappingDraftAndBurnLists() {
        CardPack active = new CardPack(0, List.of(card("a"), card("b"), card("c"), card("d")), 4, false);
        BurnPlayer p0 = player("Alice", 0, "tok-0", List.of(active), List.of(copy(active)));
        BurnGameInfo game = game(List.of(p0, player("Bob", 1, "tok-1", List.of(pack(1, "x")), List.of())),
                1, 1, BurnRemainderPolicy.BURN_EXTRAS);
        stubGame(game);

        assertThrows(BadRequestException.class, () ->
                worker.takeTurn("tok-0", "gid", turn(List.of("a"), List.of("a"))).await().indefinitely());
    }

    @Test
    void advancesGenerationAndCompletesWhenAllPacksAreEmpty() {
        CardPack packA = new CardPack(0, List.of(card("a1"), card("a2")), 2, false);
        CardPack packB = new CardPack(1, List.of(card("b1"), card("b2")), 2, false);
        BurnPlayer p0 = player("Alice", 0, "tok-0", List.of(copy(packA)), List.of(copy(packA)));
        BurnPlayer p1 = player("Bob", 1, "tok-1", List.of(copy(packB)), List.of(copy(packB)));
        BurnGameInfo game = game(List.of(p0, p1), 1, 1, BurnRemainderPolicy.BURN_EXTRAS);
        stubGame(game);
        when(dbHandler.updateGameState("gid", GameState.GAME_COMPLETE))
                .thenReturn(Uni.createFrom().item(GameState.GAME_COMPLETE));

        worker.takeTurn("tok-0", "gid", turn(List.of("a1"), List.of("a2"))).await().indefinitely();
        assertEquals(GameState.GAME_STARTED, game.getGameState());

        worker.takeTurn("tok-1", "gid", turn(List.of("b1"), List.of("b2"))).await().indefinitely();

        assertEquals(GameState.GAME_COMPLETE, game.getGameState());
        verify(dbHandler).updateGameState("gid", GameState.GAME_COMPLETE);
    }

    @Test
    void draftDataReportsTurnConfigurationAndCardsLeft() {
        CardPack dealt = new CardPack(0, List.of(card("a"), card("b"), card("c"),
                card("d"), card("e"), card("f")), 6, false);
        BurnPlayer p0 = new BurnPlayer("Alice", "acct-0", 0, List.of(dealt), List.of(),
                List.of(card("x")), List.of(card("y"), card("z")));
        p0.setPlayerToken("tok-0");
        BurnGameInfo game = new BurnGameInfo("gid", "cube-1", "burn",
                List.of(p0, player("Bob", 1, "tok-1", List.of(pack(1, "x")), List.of())),
                GameState.GAME_STARTED, Instant.now(), 0, DraftDirection.ASCENDING,
                2, 3, BurnRemainderPolicy.DRAFT_EXTRAS);
        when(dbHandler.findGame("gid")).thenReturn(Uni.createFrom().item(game));

        PlayerBurnData data = worker.getDraftData("gid", "tok-0").await().indefinitely();

        assertEquals(2, data.cardsDraftedPerPack());
        assertEquals(3, data.cardsBurnedPerPack());
        assertEquals(BurnRemainderPolicy.DRAFT_EXTRAS, data.remainderPolicy());
        BurnPlayerSnapshot snapshot = data.player();
        assertEquals("Alice", snapshot.playerName());
        assertFalse(snapshot.cardsDrafted().isEmpty());
        assertEquals(2, snapshot.cardsBurned().size());
        assertEquals(6 - 1 - 2, snapshot.cardsLeftToDraft());
    }

    // --- creation -----------------------------------------------------------

    @Test
    void rejectsCreationWhenCubeHasTooFewCards() throws java.io.IOException {
        Cube cube = org.magic.TestJson.fromJson(readResource("ZachCube.json"), Cube.class);
        when(cubeDownloader.getCubeForCubeID("cube-1")).thenReturn(Uni.createFrom().item(cube));

        List<BurnPlayerCreationInfo> players = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> new BurnPlayerCreationInfo("P" + i, "acct-" + i, null))
                .toList();
        BurnGameCreationInfo info = new BurnGameCreationInfo("cube-1", players, 300,
                2, 25, 1, 1, BurnRemainderPolicy.BURN_EXTRAS);

        assertThrows(BadRequestException.class, () -> worker.startGame(info).await().indefinitely());
    }

    // --- helpers ------------------------------------------------------------

    private void stubGame(final BurnGameInfo game) {
        when(dbHandler.findGame("gid")).thenReturn(Uni.createFrom().item(game));
        lenient().when(dbHandler.updateGame(any()))
                .thenAnswer(invocation -> Uni.createFrom().item((BurnGameInfo) invocation.getArgument(0)));
    }

    private static BurnTurnRequest turn(final List<String> draft, final List<String> burn) {
        return new BurnTurnRequest(draft, burn);
    }

    private static BurnPlayer player(final String name, final int order, final String token,
                                     final List<CardPack> dealt, final List<CardPack> active) {
        BurnPlayer player = new BurnPlayer(name, "acct-" + order, order, dealt, active, null, null);
        player.setPlayerToken(token);
        return player;
    }

    private static BurnGameInfo game(final List<BurnPlayer> players, final int draftedPerPack,
                                     final int burnedPerPack, final BurnRemainderPolicy policy) {
        return new BurnGameInfo("gid", "cube-1", "burn", players, GameState.GAME_STARTED,
                Instant.now(), 0, DraftDirection.ASCENDING, draftedPerPack, burnedPerPack, policy);
    }

    private static CardPack pack(final int number, final String... cardIDs) {
        List<Card> cards = Arrays.stream(cardIDs).map(BurnGameCoordinationWorkerTest::card).toList();
        return new CardPack(number, cards, cards.size(), false);
    }

    private static CardPack copy(final CardPack original) {
        return new CardPack(original.getPackNumber(),
                List.copyOf(original.getCardsInPack()),
                original.getOriginalCardsInPack(),
                original.getDoubleDraftedFlag());
    }

    private static Card card(final String id) {
        CardDetails details = new CardDetails("set", "Set", "sf-" + id, "s.jpg", "n.jpg", null,
                "Card " + id, "Instant", 1, List.of("r"));
        return new Card("Card " + id, details, id, 1, "Instant");
    }

    private static String readResource(final String name) {
        try (var is = BurnGameCoordinationWorkerTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(is);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
