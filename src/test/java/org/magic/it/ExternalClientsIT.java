package org.magic.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.api.scryfall.ScryfallCollectionRequest;
import org.magic.common.api.scryfall.ScryfallListResponse;
import org.magic.common.card.Cube;
import org.magic.common.cube.CubeCobraService;
import org.magic.common.cube.CubeDownloader;
import org.magic.common.external.ScryfallService;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

/**
 * End-to-end test of the external integrations over real HTTP: the reactive
 * REST clients are configured (via the test profile) to point at an in-process
 * stub that serves canned Scryfall and CubeCobra responses.
 *
 * <p>This exercises everything the unit tests mock away: config-key URL
 * resolution, the reactive client HTTP stack, JSON decoding into the DTOs,
 * Uni completion semantics, and the error path for non-2xx responses.</p>
 */
@QuarkusTest
@TestProfile(ExternalClientsIT.StubProfile.class)
class ExternalClientsIT {

    /** Points the REST clients at the local stub and keeps the cache off. */
    public static class StubProfile implements QuarkusTestProfile {
        private static final StubExternalApis STUB = createStub();

        private static StubExternalApis createStub() {
            try {
                return new StubExternalApis();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not start the external API stub", e);
            }
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "scryfallApi/mp-rest/url", STUB.scryfallBaseUrl(),
                    "cubeCobraHostName/mp-rest/url", STUB.cubeCobraBaseUrl(),
                    "scryfall.cache.enabled", "false");
        }
    }

    @Inject
    @org.eclipse.microprofile.rest.client.inject.RestClient
    ScryfallService scryfallService;

    @Inject
    @org.eclipse.microprofile.rest.client.inject.RestClient
    CubeCobraService cubeCobraService;

    @Inject
    CubeDownloader cubeDownloader;

    @BeforeEach
    void setUp() throws Exception {
        // The app runs under the "test" profile; drive CubeDownloader through its
        // prod branch so the real REST client is exercised.
        setField("activeProfile", "prod");
    }

    @AfterEach
    void tearDown() throws Exception {
        setField("activeProfile", "test");
    }

    private void setField(final String name, final String value) throws Exception {
        Field field = CubeDownloader.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(cubeDownloader, value);
    }

    // --- Scryfall -----------------------------------------------------------

    @Test
    void scryfallSearchDecodesListResponse() {
        ScryfallListResponse response = scryfallService
                .searchCards("lightning", "name", "auto", "cards", 1, false, false, false)
                .await().indefinitely();

        assertNotNull(response);
        assertEquals("list", response.object());
        assertEquals(1, response.data().size());
        assertEquals("Lightning Bolt", response.data().get(0).name());
        assertEquals("7673784e-db4b-43a1-8d55-1bb9fc1e284f", response.data().get(0).id());
    }

    @Test
    void scryfallCardByIdDecodesCard() {
        ScryfallCard card = scryfallService.getCardById("7673784e-db4b-43a1-8d55-1bb9fc1e284f")
                .await().indefinitely();

        assertEquals("Lightning Bolt", card.name());
        assertEquals("Instant", card.typeLine());
        assertEquals(1.0, card.cmc());
    }

    @Test
    void scryfallCollectionLookupPostsIdentifiers() {
        var request = new ScryfallCollectionRequest(List.of(
                new ScryfallCollectionRequest.ScryfallIdentifier("Lightning Bolt"),
                new ScryfallCollectionRequest.ScryfallIdentifier("Absent Card")));

        var response = scryfallService.lookupByCollection(request).await().indefinitely();

        assertEquals(1, response.data().size());
        assertEquals("Lightning Bolt", response.data().get(0).name());
        assertEquals(1, response.notFound().size());
        assertEquals("Absent Card", response.notFound().get(0).name());
    }

    @Test
    void scryfallNon2xxSurfacesAsFailure() {
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> scryfallService.getCardById("missing").await().indefinitely());
        assertTrue(failure instanceof WebApplicationException,
                "expected WebApplicationException, got " + failure.getClass().getName());
    }

    // --- CubeCobra ----------------------------------------------------------

    @Test
    void cubeCobraByIdDecodesCube() {
        Cube cube = cubeCobraService.getCubeDataAsJson("cube-abc").await().indefinitely();

        assertNotNull(cube);
        assertEquals("WaWa's \"Best of\" Cube (Vintage+Power)", cube.getName());
        cube.getCards().shuffleMainboard();
        assertEquals(1, cube.getCards().drawCardsFromCube(1).size());
    }

    @Test
    void cubeCobraByOwnerDecodesCubeList() {
        List<Cube> cubes = cubeCobraService.getCubeDataAsJsonByOwner("adam").await().indefinitely();

        assertEquals(1, cubes.size());
        cubes.get(0).getCards().shuffleMainboard();
        assertEquals(1, cubes.get(0).getCards().drawCardsFromCube(1).size());
    }

    @Test
    void cubeDownloaderProdBranchUsesReactiveClient() {
        Cube cube = cubeDownloader.getCubeForCubeID("cube-abc").await().indefinitely();

        assertNotNull(cube);
    }

    /**
     * The money path: POST /game (public API) -> worker -> CubeDownloader ->
     * reactive REST client -> external HTTP -> packs persisted in MongoDB.
     */
    @Test
    void creatingAGameDownloadsTheCubeOverRealHttp() {
        String gameId = "it-ext-" + UUID.randomUUID();
        JsonNode created = It.tree(given()
                .contentType("application/json")
                .body("""
                      {
                        "gameID": "%s",
                        "cubeID": "cube-abc",
                        "players": [
                          {"name": "External One", "accountID": "acct-ext-0", "playerToken": "tok-ext-p0"},
                          {"name": "External Two", "accountID": "acct-ext-1", "playerToken": "tok-ext-p1"}
                        ],
                        "numberOfDoubleDraftPicksPerPlayer": 0
                      }""".formatted(gameId))
                .when().post("/game")
                .then()
                .statusCode(200)
                .extract().asString());

        assertEquals(gameId, created.get("gameID").asText());
        assertEquals("pyramid", created.get("gameType").asText());
        // 360-card fixture -> 20 packs per player, proving the cube came from the stub
        assertEquals(20, created.get("players").get(0).get("cardPacks").size());
    }
}
