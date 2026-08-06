package org.magic.accountService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.accountService.api.DraftGameState;
import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.api.GameHistoryPlayer;
import org.magic.accountService.app.GameHistoryService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameHistoryResourceTest {

    @Mock
    GameHistoryService gameHistoryService;

    GameHistoryResource resource;

    @BeforeEach
    void setup() {
        resource = new GameHistoryResource(gameHistoryService);
    }

    @Test
    void shouldGetGameHistory() {
        var players = List.of(new GameHistoryPlayer("Alice", "Alice Smith", "acc-1", 1, 3, false, null, null));
        var entries = List.of(new GameHistoryEntry("game-1", "cube-1", "pyramid",
                DraftGameState.GAME_IN_PROGRESS, players, Instant.now()));

        when(gameHistoryService.getGameHistory("acc-1")).thenReturn(entries);

        List<GameHistoryEntry> result = resource.getGameHistory("acc-1").await().indefinitely();

        assertEquals(1, result.size());
        assertEquals("game-1", result.get(0).gameID());
        assertEquals("pyramid", result.get(0).gameType());
    }

    @Test
    void shouldReturnEmptyListWhenNoGames() {
        when(gameHistoryService.getGameHistory("acc-1")).thenReturn(List.of());

        List<GameHistoryEntry> result = resource.getGameHistory("acc-1").await().indefinitely();

        assertTrue(result.isEmpty());
    }
}
