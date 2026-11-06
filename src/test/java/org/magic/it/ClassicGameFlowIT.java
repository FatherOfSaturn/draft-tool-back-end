package org.magic.it;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.magic.common.cube.CubeDownloader;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;

/**
 * End-to-end integration test for the classic (rotisserie) draft REST API
 * against a real MongoDB. Locks the serialized JSON wire shapes, the full
 * pack-passing state machine, and the unified game-history endpoint.
 */
@QuarkusTest
class ClassicGameFlowIT {

    private static final int PLAYERS = 4;
    private static final int CARDS_PER_PACK = 6;
    private static final int PACKS_PER_PLAYER = 2;
    private static final int TOTAL_CARDS = PLAYERS * CARDS_PER_PACK * PACKS_PER_PLAYER;

    @InjectMock
    CubeDownloader cubeDownloader;

    @BeforeEach
    void stubCube() {
        when(cubeDownloader.getCubeForCubeID(anyString()))
                .thenReturn(Uni.createFrom().item(It.cube("ZachCube.json")));
    }

    private static final List<String> CARD_ORDER = List.of("name", "cardID", "details", "cmc", "type_line");

    /** A logged-in dev account whose accountID is used as a game player. */
    private record Seat(int slot, String displayName, String accountID, String jwt) {}

    /** Creates four fresh dev accounts; their accountIDs become classic players. */
    private List<Seat> seats() {
        String suffix = Long.toHexString(System.nanoTime());
        List<Seat> result = new java.util.ArrayList<>();
        for (int i = 0; i < PLAYERS; i++) {
            String name = "Classic" + suffix + "N" + i;
            JsonNode login = It.tree(given()
                    .contentType("application/json")
                    .body("{\"idToken\": \"%s\"}".formatted(
                            It.devIdToken("classic-it-sub-" + suffix + '.' + i,
                                    name.toLowerCase() + "@example.com", name)))
                    .when().post("/account/login")
                    .then()
                    .statusCode(200)
                    .extract().asString());
            result.add(new Seat(i, name,
                    login.get("account").get("accountID").asText(),
                    login.get("jwt").asText()));
        }
        assertEquals(PLAYERS, result.size());
        return result;
    }

    private JsonNode create(final List<Seat> seats, final String[] tokens) {
        StringBuilder players = new StringBuilder("[");
        for (int i = 0; i < PLAYERS; i++) {
            if (i > 0) {
                players.append(",");
            }
            players.append("{\"name\":\"").append(seats.get(i).displayName())
                    .append("\",\"accountID\":\"").append(seats.get(i).accountID())
                    .append("\",\"playerToken\":\"").append(tokens[i]).append("\"}");
        }
        players.append("]");

        JsonNode created = It.tree(given()
                .contentType("application/json")
                .body("""
                      {
                        "cubeID": "it-classic-cube",
                        "players": %s,
                        "numberOfPacks": %d,
                        "cardsPerPack": %d,
                        "packsPerPlayer": %d
                      }""".formatted(players, PLAYERS * PACKS_PER_PLAYER, CARDS_PER_PACK, PACKS_PER_PLAYER))
                .when().post("/classic-game")
                .then()
                .statusCode(200)
                .extract().asString());

        It.assertFieldOrder(created, List.of(
                "gameID", "cubeID", "gameType", "players", "gameState",
                "createdAt", "currentPackIndex", "draftDirection"));
        It.assertFieldOrder(created.get("players").get(0), List.of(
                "playerName", "draftOrderNumber", "cardsDrafted", "dealtCardPacks", "activeCardPacks",
                "accountID"));
        It.assertFieldOrder(created.get("players").get(0).get("dealtCardPacks").get(0), List.of(
                "packNumber", "cardsInPack", "originalCardsInPack", "doubleDraftedFlag"));
        It.assertFieldOrder(created.get("players").get(0).get("dealtCardPacks").get(0).get("cardsInPack").get(0),
                CARD_ORDER);
        assertEquals("classic", created.get("gameType").asText());
        assertEquals("GAME_STARTED", created.get("gameState").asText());
        assertEquals("ASCENDING", created.get("draftDirection").asText());
        assertFalse(created.toString().contains("playerToken"), "playerToken leaked in ClassicGameInfo");
        assertEquals(PACKS_PER_PLAYER, created.get("players").get(0).get("dealtCardPacks").size());
        return created;
    }

    private JsonNode draftCheck(final String gameId, final String[] tokens, final int playerIdx) {
        String body = given()
                .header("X-Player-Token", tokens[playerIdx])
                .when().get("/classic-game/{gameID}/draftCheck", gameId)
                .then()
                .statusCode(200)
                .extract().asString();
        It.assertFieldOrder(body, List.of("canDraft", "gameState"));
        return It.tree(body);
    }

    private JsonNode draftData(final String gameId, final String[] tokens, final int playerIdx) {
        String body = given()
                .header("X-Player-Token", tokens[playerIdx])
                .when().get("/classic-game/{gameID}/draftData", gameId)
                .then()
                .statusCode(200)
                .extract().asString();
        It.assertFieldOrder(body, List.of("gameID", "gameState", "draftDirection", "player"));
        JsonNode data = It.tree(body);
        It.assertFieldOrder(data.get("player"), List.of(
                "playerName", "activeCardPacks", "cardsDrafted", "cardsLeftToDraft"));
        return data;
    }

    /** Drafts one card from the player's current (first active) pack. */
    private JsonNode draftCard(final String gameId, final String[] tokens, final int playerIdx) {
        JsonNode data = draftData(gameId, tokens, playerIdx);
        JsonNode activePacks = data.get("player").get("activeCardPacks");
        assertFalse(activePacks.isEmpty(), "Player " + playerIdx + " has no active pack");
        String cardId = activePacks.get(0).get("cardsInPack").get(0).get("cardID").asText();

        String drafted = given()
                .header("X-Player-Token", tokens[playerIdx])
                .pathParam("gameID", gameId)
                .pathParam("cardID", cardId)
                .when().post("/classic-game/{gameID}/draftCard/{cardID}")
                .then()
                .statusCode(200)
                .extract().asString();
        It.assertFieldOrder(drafted, CARD_ORDER);
        return It.tree(drafted);
    }

    /** Finds the history entry of one game in the caller's /account/game/history. */
    private JsonNode historyEntry(final Seat seat, final String gameId) {
        JsonNode history = It.tree(given()
                .header("Authorization", "Bearer " + seat.jwt())
                .when().get("/account/game/history")
                .then()
                .statusCode(200)
                .extract().asString());
        for (JsonNode entry : history) {
            It.assertFieldOrder(entry, List.of(
                    "gameID", "cubeID", "gameType", "gameState", "players", "createdAt"));
            It.assertFieldOrder(entry.get("players").get(0), List.of(
                    "name", "displayName", "accountID", "currentPack", "totalPacks",
                    "doneDrafting", "cardsLeftToDraft", "draftOrderNumber"));
            if (gameId.equals(entry.get("gameID").asText())) {
                return entry;
            }
        }
        throw new AssertionError("History of " + seat.displayName() + " contains no game " + gameId
                + "; entries: " + history.size());
    }

    @Test
    void wireShapesAndOneDraftPerSeat() {
        List<Seat> seats = seats();
        String[] tokens = new String[PLAYERS];
        for (int i = 0; i < PLAYERS; i++) {
            tokens[i] = "tok-one-" + i;
        }
        JsonNode game = create(seats, tokens);
        String gameId = game.get("gameID").asText();

        JsonNode check = draftCheck(gameId, tokens, 0);
        assertEquals(true, check.get("canDraft").asBoolean());
        assertEquals("GAME_STARTED", check.get("gameState").asText());

        JsonNode data = draftData(gameId, tokens, 0);
        assertEquals(CARDS_PER_PACK * PACKS_PER_PLAYER, data.get("player").get("cardsLeftToDraft").asInt());

        // One card drafted per player: everyone holds exactly one active pack after the round.
        for (int p = 0; p < PLAYERS; p++) {
            draftCard(gameId, tokens, p);
        }
        for (int p = 0; p < PLAYERS; p++) {
            assertEquals(true, draftCheck(gameId, tokens, p).get("canDraft").asBoolean());
        }

        // All games in flight cannot be fetched (409); draft-check works mid-flight
        assertNotNull(draftCheck(gameId, tokens, 1).get("gameState"));

        // Unified history maps the in-flight game for the drafting account
        JsonNode entry = historyEntry(seats.get(0), gameId);
        assertEquals("classic", entry.get("gameType").asText());
        assertEquals("game_in_progress", entry.get("gameState").asText());

        // End endpoint shape
        JsonNode end = It.tree(given()
                .when().post("/classic-game/end/{gameID}", gameId)
                .then()
                .statusCode(200)
                .extract().asString());
        It.assertFieldOrder(end, List.of("gameID", "gameState"));
        assertEquals("GAME_COMPLETE", end.get("gameState").asText());
    }

    @Test
    void fullGameAdvancesGenerationsAndCompletes() {
        List<Seat> seats = seats();
        String[] tokens = new String[PLAYERS];
        String suffix = Long.toHexString(System.nanoTime());
        for (int i = 0; i < PLAYERS; i++) {
            tokens[i] = "tok-full-" + suffix + "-" + i;
        }
        JsonNode game = create(seats, tokens);
        String gameId = game.get("gameID").asText();

        int drafted = 0;
        while (drafted < TOTAL_CARDS + PLAYERS) {
            if ("GAME_COMPLETE".equals(draftCheck(gameId, tokens, 0).get("gameState").asText())) {
                break;
            }
            for (int p = 0; p < PLAYERS && drafted < TOTAL_CARDS; p++) {
                if (draftCheck(gameId, tokens, p).get("canDraft").asBoolean()) {
                    draftCard(gameId, tokens, p);
                    drafted++;
                }
            }
        }

        // Completed games can now be fetched directly
        JsonNode fetched = It.tree(get("/classic-game/fetchGameData/" + gameId)
                .then().statusCode(200).extract().asString());
        assertEquals("GAME_COMPLETE", fetched.get("gameState").asText());
        assertEquals(TOTAL_CARDS, drafted);

        JsonNode entry = historyEntry(seats.get(0), gameId);
        assertEquals("classic", entry.get("gameType").asText());
        assertEquals("game_complete", entry.get("gameState").asText());
        assertEquals(true, entry.get("players").get(0).get("doneDrafting").asBoolean());
        assertEquals(0, entry.get("players").get(0).get("cardsLeftToDraft").asInt());
    }
}
