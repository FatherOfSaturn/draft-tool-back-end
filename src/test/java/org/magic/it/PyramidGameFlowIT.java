package org.magic.it;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.magic.pyramidDraft.app.CubeDownloader;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;

/**
 * End-to-end integration test for the pyramid draft REST API, running against a
 * real MongoDB. Locks the serialized JSON shapes of every response so refactors
 * cannot silently change the wire format.
 */
@QuarkusTest
class PyramidGameFlowIT {

    @InjectMock
    CubeDownloader cubeDownloader;

    @BeforeEach
    void stubCube() {
        when(cubeDownloader.getCubeForCubeID(anyString()))
                .thenReturn(Uni.createFrom().item(It.cube("ZachCube.json")));
    }

    /** Creates a fresh pyramid game with two players and returns its game ID. */
    private String createGame() {
        String gameId = "it-" + UUID.randomUUID();
        given()
            .contentType("application/json")
            .body("""
                  {
                    "gameID": "%s",
                    "cubeID": "it-pyramid-cube",
                    "players": [
                      {"name": "Player Zero", "accountID": "acct-it-0", "playerToken": "tok-it-p0"},
                      {"name": "Player One", "accountID": "acct-it-1", "playerToken": "tok-it-p1"}
                    ],
                    "numberOfDoubleDraftPicksPerPlayer": 0
                  }""".formatted(gameId))
            .when().post("/game")
            .then()
            .statusCode(200);
        return gameId;
    }

    private JsonNode game(final String gameId) {
        return It.tree(get("/game/fetchGameData/" + gameId).then().statusCode(200).extract().asString());
    }

    /** Card JSON produced by every draftCard call: {name, cardID, details, cmc, type_line}. */
    private static final List<String> CARD_ORDER = List.of("name", "cardID", "details", "cmc", "type_line");

    private void draft(final String gameId, final String token, final int packNumber, final String cardID) {
        String draft = given()
                .header("X-Player-Token", token)
                .pathParam("gameID", gameId)
                .pathParam("packNumber", packNumber)
                .pathParam("cardID", cardID)
                .queryParam("doublePick", false)
                .when().post("/game/{gameID}/draftCard/{packNumber}/{cardID}")
                .then()
                .statusCode(200)
                .extract().asString();
        It.assertFieldOrder(draft, CARD_ORDER);
    }

    @Test
    void fullLifecycleProducesTheExpectedWireShapes() {
        String gameId = createGame();

        // --- Creation shape -------------------------------------------------
        JsonNode game = game(gameId);
        It.assertFieldOrder(game, List.of("gameID", "cubeID", "gameType", "players", "gameState", "createdAt"));
        It.assertFieldOrder(game.get("players").get(0),
                List.of("playerName", "cardsDrafted", "cardPacks", "doubleDraftPicksRemaining", "phase1TotalPacks", "phase2TotalPacks",
                        "accountID", "currentDraftPack", "readyForMerge"));
        It.assertFieldOrder(game.get("players").get(0).get("cardPacks").get(0),
                List.of("packNumber", "cardsInPack", "originalCardsInPack", "doubleDraftedFlag"));
        It.assertFieldOrder(game.get("players").get(0).get("cardPacks").get(0).get("cardsInPack").get(0), CARD_ORDER);

        // The 360-card fixture lands in the "small" band: 20 packs per player.
        assertEquals(20, game.get("players").get(0).get("cardPacks").size());
        assertEquals(20, game.get("players").get(1).get("cardPacks").size());
        assertEquals("GAME_STARTED", game.get("gameState").asText());
        assertEquals("pyramid", game.get("gameType").asText());

        // The secret token is never serialized.
        assertFalse(game.toString().contains("playerToken"), "playerToken leaked in GameInfo");

        // --- Not-ready merge returns GAME_STARTED ---------------------------
        JsonNode merge = It.tree(get("/game/merge/" + gameId).then().statusCode(200).extract().asString());
        It.assertFieldOrder(merge, List.of("gameID", "gameState"));
        assertEquals("GAME_STARTED", merge.get("gameState").asText());

        // --- Phase 1: one pick from each of the 20 packs, per player ---------
        draftPhase(gameId, "tok-it-p0", 20);
        draftPhase(gameId, "tok-it-p1", 20);

        // --- Merge and swap -> GAME_MERGED ----------------------------------
        merge = It.tree(get("/game/merge/" + gameId).then().statusCode(200).extract().asString());
        It.assertFieldOrder(merge, List.of("gameID", "gameState"));
        assertEquals("GAME_MERGED", game(gameId).get("gameState").asText());

        // --- Phase 2: merged packs (13 per player), then auto-complete ------
        draftMergedPhase(gameId, "tok-it-p0");
        draftMergedPhase(gameId, "tok-it-p1");
        assertEquals("GAME_COMPLETE", game(gameId).get("gameState").asText());

        // --- End endpoint shape ----------------------------------------------
        JsonNode end = It.tree(get("/game/end/" + gameId).then().statusCode(200).extract().asString());
        It.assertFieldOrder(end, List.of("gameID", "gameState"));
        assertEquals("GAME_COMPLETE", end.get("gameState").asText());
    }

    /** Drafts one card from every pack (by packNumber) until the player is merge-ready. */
    private void draftPhase(final String gameId, final String token, final int packCount) {
        for (int packNumber = 0; packNumber < packCount; packNumber++) {
            JsonNode player = game(gameId).get("players").get("tok-it-p0".equals(token) ? 0 : 1);
            JsonNode pack = findPackByNumber(player, packNumber);
            draft(gameId, token, packNumber, pack.get("cardsInPack").get(0).get("cardID").asText());
        }
    }

    /** Drafts one card from every pack of a merged/renumbered pack list, in array order. */
    private void draftMergedPhase(final String gameId, final String token) {
        int playerIdx = "tok-it-p0".equals(token) ? 0 : 1;
        int packs = game(gameId).get("players").get(playerIdx).get("cardPacks").size();
        for (int i = 0; i < packs; i++) {
            JsonNode pack = game(gameId).get("players").get(playerIdx).get("cardPacks").get(i);
            if (pack.get("cardsInPack").isEmpty()) {
                continue; // empty merged pack: nothing to draft
            }
            draft(gameId, token, pack.get("packNumber").asInt(), pack.get("cardsInPack").get(0).get("cardID").asText());
            if ("GAME_COMPLETE".equals(game(gameId).get("gameState").asText())) {
                return;
            }
        }
    }

    private static JsonNode findPackByNumber(final JsonNode player, final int packNumber) {
        for (JsonNode pack : player.get("cardPacks")) {
            if (pack.get("packNumber").asInt() == packNumber) {
                return pack;
            }
        }
        throw new AssertionError("Pack " + packNumber + " not present for " + player);
    }
}
