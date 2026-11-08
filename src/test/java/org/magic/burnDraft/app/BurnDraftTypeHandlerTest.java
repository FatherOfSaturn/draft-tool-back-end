package org.magic.burnDraft.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.burnDraft.api.BurnGameCreationInfo;
import org.magic.burnDraft.api.BurnGameInfo;
import org.magic.burnDraft.api.BurnRemainderPolicy;
import org.magic.burnDraft.app.GameCoordination.BurnGameCoordinationWorker;
import org.magic.lobbyService.api.LobbyPlayer;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class BurnDraftTypeHandlerTest {

    @Mock
    BurnGameCoordinationWorker gameWorker;

    BurnDraftTypeHandler handler;

    @BeforeEach
    void setup() {
        handler = new BurnDraftTypeHandler(gameWorker);
    }

    @Test
    void exposesBurnDraftMetadata() {
        assertEquals("burn", handler.draftType());
        assertEquals(2, handler.defaultMinPlayers());
        assertEquals(12, handler.defaultMaxPlayers());
    }

    @Test
    void appliesDefaultsAndDerivesPackCount() {
        BurnGameInfo game = mock(BurnGameInfo.class);
        when(game.getGameID()).thenReturn("gid");
        when(gameWorker.startGame(any())).thenReturn(Uni.createFrom().item(game));

        List<LobbyPlayer> players = List.of(
                lobbyPlayer("acc-1", "Alice", 0, "tok-a"),
                lobbyPlayer("acc-2", "Bob", 1, "tok-b"),
                lobbyPlayer(null, "Carol", 2, "tok-c"));

        String gameID = handler.startGame("cube-1", players, Map.of("cubeID", "cube-1"))
                .await().indefinitely();

        assertEquals("gid", gameID);
        BurnGameCreationInfo info = capturedCreationInfo();
        assertEquals("cube-1", info.cubeID());
        assertEquals(3, info.players().size());
        assertEquals(3 * 3, info.numberOfPacks(), "players * default packsPerPlayer");
        assertEquals(15, info.cardsPerPack());
        assertEquals(3, info.packsPerPlayer());
        assertEquals(1, info.cardsDraftedPerPack());
        assertEquals(1, info.cardsBurnedPerPack());
        assertEquals(BurnRemainderPolicy.BURN_EXTRAS, info.remainderPolicy());
    }

    @Test
    void honoursLobbyOverridesAndDraftExtrasPolicy() {
        BurnGameInfo game = mock(BurnGameInfo.class);
        when(game.getGameID()).thenReturn("gid");
        when(gameWorker.startGame(any())).thenReturn(Uni.createFrom().item(game));

        List<LobbyPlayer> players = List.of(
                lobbyPlayer(null, "Bob", 1, "tok-b"),
                lobbyPlayer("acc-1", "Alice", 0, "tok-a"));

        handler.startGame("cube-9", players, Map.of(
                "cubeID", "cube-9",
                "cardsPerPack", 7,
                "packsPerPlayer", 2,
                "cardsDraftedPerPack", 2,
                "cardsBurnedPerPack", 3,
                "remainderPolicy", "draft_extras")).await().indefinitely();

        BurnGameCreationInfo info = capturedCreationInfo();
        assertEquals(7, info.cardsPerPack());
        assertEquals(2, info.packsPerPlayer());
        assertEquals(4, info.numberOfPacks());
        assertEquals(2, info.cardsDraftedPerPack());
        assertEquals(3, info.cardsBurnedPerPack());
        assertEquals(BurnRemainderPolicy.DRAFT_EXTRAS, info.remainderPolicy());
        // players are ordered by lobby slot
        assertEquals("Alice", info.players().get(0).playerName());
        assertEquals("Bob", info.players().get(1).playerName());
    }

    @Test
    void rejectsInvalidRemainderPolicy() {
        List<LobbyPlayer> players = List.of(lobbyPlayer("acc-1", "Alice", 0, "tok-a"));

        assertThrows(IllegalStateException.class, () -> handler.startGame("cube-1", players,
                Map.of("cubeID", "cube-1", "remainderPolicy", "not-a-policy")));
    }

    private BurnGameCreationInfo capturedCreationInfo() {
        ArgumentCaptor<BurnGameCreationInfo> captor = ArgumentCaptor.forClass(BurnGameCreationInfo.class);
        org.mockito.Mockito.verify(gameWorker).startGame(captor.capture());
        return captor.getValue();
    }

    private static LobbyPlayer lobbyPlayer(final String accountID, final String displayName,
                                           final int slotIndex, final String playerToken) {
        return new LobbyPlayer(accountID, displayName, slotIndex, playerToken, Instant.now());
    }
}
