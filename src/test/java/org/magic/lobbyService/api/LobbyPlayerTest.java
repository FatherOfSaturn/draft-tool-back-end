package org.magic.lobbyService.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.magic.common.util.JsonUtility;

class LobbyPlayerTest {

    @Test
    void shouldNotSerializePlayerToken() {
        LobbyPlayer player = new LobbyPlayer("acc-1", "Alice", 0, "super-secret-token", Instant.now());

        String json = JsonUtility.getInstance().toJson(player);

        assertNotNull(json);
        assertFalse(json.contains("super-secret-token"));
        assertFalse(json.contains("playerToken"));
        assertFalse(json.contains("token"));
    }
}
