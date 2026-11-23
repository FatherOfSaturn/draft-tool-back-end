package org.magic.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.magic.TestJson;
import org.magic.burnDraft.api.BurnRemainderPolicy;
import org.magic.common.cube.CubeDownloader;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;

/**
 * End-to-end test of the burn draft REST API against a real MongoDB. Runs the
 * full rotating lifecycle (draft + burn + pass + generation advance + complete)
 * and locks the serialized wire shapes. Requires MongoDB (tag {@code mongo}).
 */
@QuarkusTest
@Tag("mongo")
class BurnGameFlowIT {

    private static final int PLAYERS = 4;
    private static final int PACKS_PER_PLAYER = 2;
    private static final int CARDS_PER_PACK = 6;

    @InjectMock
    CubeDownloader cubeDownloader;

    @BeforeEach
    void stubCube() {
        when(cubeDownloader.getCubeForCubeID(anyString()))
                .thenReturn(Uni.createFrom().item(It.cube("ZachCube.json")));
    }

    @Test
    void fullLifecycleDraftAndBurn() {
        String suffix = Long.toHexString(System.nanoTime());
        List<String> names = seatNames(suffix);
        List<String> tokens = tokens(suffix);
        String body = creationPayload(names, tokens, PLAYERS, PACKS_PER_PLAYER, CARDS_PER_PACK,
                1, 1, "burn_extras");

        JsonNode game = It.tree(given()
                .contentType("application/json")
                .body(body)
                .when().post("/burn-game")
                .then()
                .statusCode(200)
                .extract().asString());

        String gameId = game.get("gameID").asText();
        It.assertFieldOrder(game, List.of("gameID", "cubeID", "gameType", "players", "gameState",
                "createdAt", "currentPackIndex", "draftDirection", "cardsDraftedPerPack",
                "cardsBurnedPerPack", "remainderPolicy"));
        It.assertFieldOrder(game.get("players").get(0), List.of(
                "playerName", "draftOrderNumber", "cardsDrafted", "cardsBurned",
                "dealtCardPacks", "activeCardPacks", "accountID"));
        It.assertFieldOrder(game.get("players").get(0).get("dealtCardPacks").get(0), List.of(
                "packNumber", "cardsInPack", "originalCardsInPack", "doubleDraftedFlag"));
        It.assertFieldOrder(game.get("players").get(0).get("dealtCardPacks").get(0).get("cardsInPack").get(0),
                List.of("name", "cardID", "details", "cmc", "type_line"));

        assertEquals("burn", game.get("gameType").asText());
        assertEquals("GAME_STARTED", game.get("gameState").asText());
        assertEquals("ASCENDING", game.get("draftDirection").asText());
        assertEquals(1, game.get("cardsDraftedPerPack").asInt());
        assertEquals(1, game.get("cardsBurnedPerPack").asInt());
        assertEquals("BURN_EXTRAS", game.get("remainderPolicy").asText());
        assertEquals(PACKS_PER_PLAYER, game.get("players").get(0).get("dealtCardPacks").size());
        assertEquals(1, game.get("players").get(0).get("activeCardPacks").size());
        assertEquals(CARDS_PER_PACK,
                game.get("players").get(0).get("activeCardPacks").get(0).get("cardsInPack").size());
        assertEquals(0, game.get("players").get(0).get("cardsDrafted").size());
        assertEquals(0, game.get("players").get(0).get("cardsBurned").size());
        assertFalse(game.toString().contains("playerToken"), "playerToken leaked in BurnGameInfo");

        JsonNode data = draftData(gameId, tokens.get(0));
        assertEquals(1, data.get("cardsDraftedPerPack").asInt());
        assertEquals(1, data.get("cardsBurnedPerPack").asInt());
        assertEquals("BURN_EXTRAS", data.get("remainderPolicy").asText());
        assertEquals(PACKS_PER_PLAYER * CARDS_PER_PACK, data.get("player").get("cardsLeftToDraft").asInt());

        playToCompletion(gameId, tokens, 200);

        JsonNode finished = It.tree(given()
                .when().get("/burn-game/fetchGameData/" + gameId)
                .then().statusCode(200).extract().asString());
        assertEquals("GAME_COMPLETE", finished.get("gameState").asText());

        JsonNode history = It.tree(given()
                .when().get("/burn-game/history/" + names.get(0))
                .then().statusCode(200).extract().asString());
        assertEquals(1, history.size());
        It.assertFieldOrder(history.get(0), List.of("gameID", "cubeID", "gameState", "playerName",
                "draftOrderNumber", "cardsDraftedCount", "cardsBurnedCount", "totalPacks",
                "currentPackIndex", "createdAt"));
        assertEquals("GAME_COMPLETE", history.get(0).get("gameState").asText());
        assertEquals(6, history.get(0).get("cardsDraftedCount").asInt());
        assertEquals(6, history.get(0).get("cardsBurnedCount").asInt());
        assertEquals(PACKS_PER_PLAYER, history.get(0).get("totalPacks").asInt());

        JsonNode end = It.tree(given()
                .when().post("/burn-game/end/" + gameId)
                .then().statusCode(200).extract().asString());
        It.assertFieldOrder(end, List.of("gameID", "gameState"));
        assertEquals("GAME_COMPLETE", end.get("gameState").asText());
    }

    @Test
    void partialPassHonoursDraftExtras() {
        String suffix = Long.toHexString(System.nanoTime());
        List<String> names = seatNames(suffix).subList(0, 2);
        List<String> tokens = tokens(suffix).subList(0, 2);
        String body = creationPayload(names, tokens, 2, 1, 7, 2, 2, "draft_extras");

        JsonNode game = It.tree(given()
                .contentType("application/json")
                .body(body)
                .when().post("/burn-game")
                .then()
                .statusCode(200)
                .extract().asString());
        String gameId = game.get("gameID").asText();
        assertEquals("DRAFT_EXTRAS", game.get("remainderPolicy").asText());
        assertEquals(7, game.get("players").get(0).get("dealtCardPacks").get(0)
                .get("cardsInPack").size());

        // An unknown player token is rejected on every player-scoped endpoint.
        given().header("X-Player-Token", "not-a-token")
                .when().get("/burn-game/" + gameId + "/draftCheck")
                .then().statusCode(401);
        given().header("X-Player-Token", "not-a-token")
                .when().get("/burn-game/" + gameId + "/draftData")
                .then().statusCode(401);
        given().contentType("application/json").header("X-Player-Token", "not-a-token")
                .body(TestJson.toJson(Map.of("draftCardIds", List.of(), "burnCardIds", List.of())))
                .when().post("/burn-game/" + gameId + "/draft")
                .then().statusCode(401);

        // The first pack requires drafting 2 and burning 2; an incomplete turn is rejected.
        JsonNode data = draftData(gameId, tokens.get(0));
        JsonNode firstPack = data.get("player").get("activeCardPacks").get(0);
        List<String> cardIds = cardIds(firstPack);
        given()
                .contentType("application/json")
                .header("X-Player-Token", tokens.get(0))
                .body(TestJson.toJson(Map.of(
                        "draftCardIds", List.of(cardIds.get(0)),
                        "burnCardIds", List.of())))
                .when().post("/burn-game/" + gameId + "/draft")
                .then()
                .statusCode(400);

        playToCompletion(gameId, tokens, 50);

        JsonNode history = It.tree(given()
                .when().get("/burn-game/history/" + names.get(0))
                .then().statusCode(200).extract().asString());
        assertEquals(1, history.size());
        assertEquals("GAME_COMPLETE", history.get(0).get("gameState").asText());
        assertEquals(5, history.get(0).get("cardsDraftedCount").asInt());
        assertEquals(2, history.get(0).get("cardsBurnedCount").asInt());
    }

    @Test
    void rejectsConfigurationThatExceedsTheCube() {
        String suffix = Long.toHexString(System.nanoTime());
        List<String> names = seatNames(suffix, 12);
        List<String> tokens = tokens(suffix, 12);
        // 12 players * 25 packs * 2 cards = 600 cards > the 360-card fixture.
        String body = creationPayload(names, tokens, 12, 25, 2, 1, 1, "burn_extras");

        given()
                .contentType("application/json")
                .body(body)
                .when().post("/burn-game")
                .then()
                .statusCode(400);
    }

    // --- driving -----------------------------------------------------------

    private JsonNode draftCheck(final String gameId, final String token) {
        String body = given()
                .header("X-Player-Token", token)
                .when().get("/burn-game/" + gameId + "/draftCheck")
                .then()
                .statusCode(200)
                .extract().asString();
        It.assertFieldOrder(body, List.of("canDraft", "gameState"));
        return It.tree(body);
    }

    private JsonNode draftData(final String gameId, final String token) {
        String body = given()
                .header("X-Player-Token", token)
                .when().get("/burn-game/" + gameId + "/draftData")
                .then()
                .statusCode(200)
                .extract().asString();
        It.assertFieldOrder(body, List.of("gameID", "gameState", "draftDirection",
                "cardsDraftedPerPack", "cardsBurnedPerPack", "remainderPolicy", "player"));
        JsonNode data = It.tree(body);
        It.assertFieldOrder(data.get("player"), List.of("playerName", "activeCardPacks",
                "cardsDrafted", "cardsBurned", "cardsLeftToDraft"));
        return data;
    }

    /** Takes one turn for the player, splitting the held pack according to the game rules. */
    private void takeTurn(final String gameId, final String token) {
        JsonNode data = draftData(gameId, token);
        JsonNode pack = data.get("player").get("activeCardPacks").get(0);
        List<String> cardIds = cardIds(pack);
        int remaining = cardIds.size();

        int draftQuota = data.get("cardsDraftedPerPack").asInt();
        int burnQuota = data.get("cardsBurnedPerPack").asInt();
        BurnRemainderPolicy policy = BurnRemainderPolicy.valueOf(data.get("remainderPolicy").asText());

        int toDraft = Math.min(draftQuota, remaining);
        int toBurn = 0;
        if (remaining >= draftQuota + burnQuota) {
            toDraft = draftQuota;
            toBurn = burnQuota;
        } else {
            int extras = remaining - toDraft;
            if (policy == BurnRemainderPolicy.DRAFT_EXTRAS) {
                toDraft += extras;
            } else {
                toBurn = extras;
            }
        }

        List<String> draftIds = new ArrayList<>(cardIds.subList(0, toDraft));
        List<String> burnIds = new ArrayList<>(cardIds.subList(toDraft, toDraft + toBurn));

        String drafted = given()
                .contentType("application/json")
                .header("X-Player-Token", token)
                .body(TestJson.toJson(Map.of("draftCardIds", draftIds, "burnCardIds", burnIds)))
                .when().post("/burn-game/" + gameId + "/draft")
                .then()
                .statusCode(200)
                .extract().asString();
        JsonNode cards = It.tree(drafted);
        assertEquals(toDraft, cards.size());
        for (JsonNode card : cards) {
            It.assertFieldOrder(card, List.of("name", "cardID", "details", "cmc", "type_line"));
        }
    }

    private void playToCompletion(final String gameId, final List<String> tokens, final int maxTurns) {
        int turns = 0;
        while (turns < maxTurns) {
            if ("GAME_COMPLETE".equals(draftCheck(gameId, tokens.get(0)).get("gameState").asText())) {
                return;
            }
            boolean acted = false;
            for (String token : tokens) {
                JsonNode check = draftCheck(gameId, token);
                if (!check.get("canDraft").asBoolean()) {
                    continue;
                }
                takeTurn(gameId, token);
                acted = true;
                turns++;
            }
            assertTrue(acted, "No player could take a turn but the game is not complete");
        }
        throw new AssertionError("Burn draft did not complete within " + maxTurns + " turns");
    }

    private static List<String> cardIds(final JsonNode pack) {
        List<String> ids = new ArrayList<>();
        for (JsonNode card : pack.get("cardsInPack")) {
            ids.add(card.get("cardID").asText());
        }
        return ids;
    }

    // --- payload helpers ---------------------------------------------------

    private static List<String> seatNames(final String suffix) {
        return seatNames(suffix, PLAYERS);
    }

    private static List<String> seatNames(final String suffix, final int count) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add("Burn" + suffix + "P" + i);
        }
        return names;
    }

    private static List<String> tokens(final String suffix) {
        return tokens(suffix, PLAYERS);
    }

    private static List<String> tokens(final String suffix, final int count) {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tokens.add("tok-burn-" + suffix + "-" + i);
        }
        return tokens;
    }

    private static String creationPayload(final List<String> names, final List<String> tokens,
                                          final int players, final int packsPerPlayer,
                                          final int cardsPerPack, final int draftedPerPack,
                                          final int burnedPerPack, final String remainderPolicy) {
        List<Map<String, Object>> seats = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            Map<String, Object> seat = new LinkedHashMap<>();
            seat.put("name", names.get(i));
            seat.put("accountID", "acct-burn-" + i);
            seat.put("playerToken", tokens.get(i));
            seats.add(seat);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cubeID", "it-burn-cube");
        payload.put("players", seats);
        payload.put("numberOfPacks", players * packsPerPlayer);
        payload.put("cardsPerPack", cardsPerPack);
        payload.put("packsPerPlayer", packsPerPlayer);
        payload.put("cardsDraftedPerPack", draftedPerPack);
        payload.put("cardsBurnedPerPack", burnedPerPack);
        payload.put("remainderPolicy", remainderPolicy);
        return TestJson.toJson(payload);
    }
}
