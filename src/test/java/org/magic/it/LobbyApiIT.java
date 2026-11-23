package org.magic.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.magic.common.cube.CubeDownloader;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;

/**
 * End-to-end integration test for the lobby REST API against a real MongoDB.
 * Locks the serialized JSON wire shapes and the lobby lifecycle, including
 * starting a real pyramid draft game from the lobby.
 */
@QuarkusTest
@Tag("mongo")
class LobbyApiIT {

    @InjectMock
    CubeDownloader cubeDownloader;

    @BeforeEach
    void stubCube() {
        when(cubeDownloader.getCubeForCubeID(anyString()))
                .thenReturn(Uni.createFrom().item(It.cube("ZachCube.json")));
    }

    private record LobbyCtx(String lobbyCode, String hostToken, JsonNode lobby) {}

    /** Creates a lobby with room for 3 players and returns the host's context. */
    private LobbyCtx createLobby() {
        JsonNode response = It.tree(given()
                .contentType("application/json")
                .body("""
                      {
                        "draftType": "pyramid",
                        "config": {"cubeID": "it-pyramid-cube", "numberOfDoubleDraftPicksPerPlayer": 0},
                        "hostAccountID": "acct-lobby-host",
                        "hostDisplayName": "Host",
                        "minPlayers": 2,
                        "maxPlayers": 3
                      }""")
                .when().post("/lobby")
                .then()
                .statusCode(201)
                .extract().asString());

        It.assertFieldOrder(response, List.of("lobby", "playerToken"));
        JsonNode lobby = response.get("lobby");
        It.assertFieldOrder(lobby, List.of(
                "lobbyCode", "draftType", "config", "hostAccountID", "players",
                "minPlayers", "maxPlayers", "gameID", "status", "createdAt", "hostAloneSince"));
        It.assertFieldOrder(lobby.get("players").get(0), List.of(
                "accountID", "displayName", "slotIndex", "lastPollAt"));
        assertFalse(lobby.toString().contains("playerToken"), "lobby playerToken leaked");
        assertNotNull(response.get("playerToken").asText());
        assertEquals("WAITING", lobby.get("status").asText());
        assertEquals(2, lobby.get("minPlayers").asInt());
        assertEquals(3, lobby.get("maxPlayers").asInt());
        return new LobbyCtx(lobby.get("lobbyCode").asText(), response.get("playerToken").asText(), lobby);
    }

    private JsonNode join(final String lobbyCode, final String body) {
        JsonNode response = It.tree(given()
                .contentType("application/json")
                .body(body)
                .when().post("/lobby/{lobbyCode}/join", lobbyCode)
                .then()
                .statusCode(200)
                .extract().asString());
        It.assertFieldOrder(response, List.of("lobby", "playerToken"));
        return response;
    }

    @Test
    void fullLobbyLifecycleWithGuests() {
        LobbyCtx ctx = createLobby();

        JsonNode join1 = join(ctx.lobbyCode(), "{\"displayName\": \"Guest B\"}");
        JsonNode join2 = join(ctx.lobbyCode(), "{\"displayName\": \"Guest C\", \"accountID\": \"acct-c\"}");
        String guestTokenB = join1.get("playerToken").asText();
        String guestTokenC = join2.get("playerToken").asText();
        assertNotEquals(ctx.hostToken(), guestTokenB);
        assertEquals(3, join2.get("lobby").get("players").size());

        // --- Poll with heartbeat ---------------------------------------------
        JsonNode polled = It.tree(given()
                .header("X-Player-Token", ctx.hostToken())
                .when().get("/lobby/{lobbyCode}", ctx.lobbyCode())
                .then()
                .statusCode(200)
                .extract().asString());
        It.assertFieldOrder(polled, List.of(
                "lobbyCode", "draftType", "config", "hostAccountID", "players",
                "minPlayers", "maxPlayers", "gameID", "status", "createdAt", "hostAloneSince"));
        assertEquals("WAITING", polled.get("status").asText());

        // --- Kick: host kicks slot 2, non-host gets 403 ----------------------
        JsonNode kicked = It.tree(given()
                .contentType("application/json")
                .body("{\"playerToken\": \"%s\", \"targetSlotIndex\": 2}".formatted(ctx.hostToken()))
                .when().post("/lobby/{lobbyCode}/kick", ctx.lobbyCode())
                .then()
                .statusCode(200)
                .extract().asString());
        It.assertFieldOrder(kicked, List.of(
                "lobbyCode", "draftType", "config", "hostAccountID", "players",
                "minPlayers", "maxPlayers", "gameID", "status", "createdAt", "hostAloneSince"));
        assertEquals(2, kicked.get("players").size());
        assertFalse(kicked.toString().contains("playerToken"), "kicked lobby leaked playerToken");

        given()
                .contentType("application/json")
                .body("{\"playerToken\": \"%s\", \"targetSlotIndex\": 0}".formatted(guestTokenB))
                .when().post("/lobby/{lobbyCode}/kick", ctx.lobbyCode())
                .then()
                .statusCode(403);

        // --- Rejoin + leave ---------------------------------------------------
        String c2 = join(ctx.lobbyCode(), "{\"displayName\": \"Guest C\"}").get("playerToken").asText();
        JsonNode afterLeave = It.tree(given()
                .contentType("application/json")
                .body("{\"playerToken\": \"%s\"}".formatted(c2))
                .when().post("/lobby/{lobbyCode}/leave", ctx.lobbyCode())
                .then()
                .statusCode(200)
                .extract().asString());
        assertEquals(2, afterLeave.get("players").size());

        // --- Start: non-host is forbidden, host starts -----------------------
        given()
                .contentType("application/json")
                .body("{\"playerToken\": \"%s\"}".formatted(guestTokenB))
                .when().post("/lobby/{lobbyCode}/start", ctx.lobbyCode())
                .then()
                .statusCode(403);

        JsonNode started = It.tree(given()
                .contentType("application/json")
                .body("{\"playerToken\": \"%s\"}".formatted(ctx.hostToken()))
                .when().post("/lobby/{lobbyCode}/start", ctx.lobbyCode())
                .then()
                .statusCode(200)
                .extract().asString());
        assertEquals("STARTED", started.get("status").asText());
        assertFalse(started.get("gameID").asText().isBlank(), "started lobby must carry a gameID");
        assertTrue(started.toString().contains("it-pyramid-cube"));

        // Starting again conflicts
        given()
                .contentType("application/json")
                .body("{\"playerToken\": \"%s\"}".formatted(ctx.hostToken()))
                .when().post("/lobby/{lobbyCode}/start", ctx.lobbyCode())
                .then()
                .statusCode(409);

        // The started lobbies with invalid code are 404
        given().when().get("/lobby/nocodex").then().statusCode(404);
    }

    @Test
    void badDraftTypeIsRejected() {
        given()
                .contentType("application/json")
                .body("""
                      {
                        "draftType": "not-a-format",
                        "config": {"cubeID": "it-pyramid-cube"},
                        "hostAccountID": "acct-lobby-host",
                        "hostDisplayName": "Host",
                        "minPlayers": 2,
                        "maxPlayers": 2
                      }""")
                .when().post("/lobby")
                .then()
                .statusCode(400);
    }
}
