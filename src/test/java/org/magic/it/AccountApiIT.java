package org.magic.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end integration test for the account REST API against a real MongoDB.
 * Uses dev-mode Google ID tokens (see %test config) and locks the serialized
 * JSON wire shapes, the JWT auth flow, and deck CRUD.
 */
@QuarkusTest
@Tag("mongo")
class AccountApiIT {

    private record Login(String jwt, JsonNode account, String sub) {}

    /** Logs in (creating the account if needed) and returns the JWT + account. */
    private Login login(final String sub, final String email, final String name) {
        JsonNode response = It.tree(given()
                .contentType("application/json")
                .body("{\"idToken\": \"%s\"}".formatted(It.devIdToken(sub, email, name)))
                .when().post("/account/login")
                .then()
                .statusCode(200)
                .extract().asString());

        It.assertFieldOrder(response, List.of("account", "jwt", "expiresIn"));
        It.assertFieldOrder(response.get("account"), List.of(
                "accountID", "displayName", "deckIDs", "createdAt"));
        String body = response.toString();
        assertFalse(body.contains("\"email\""), "email leaked from account");
        assertFalse(body.contains("\"googleSub\""), "googleSub leaked from account");
        assertTrue(response.get("expiresIn").asLong() > 0);
        return new Login(response.get("jwt").asText(), response.get("account"), sub);
    }

    @Test
    void loginAccountAndDeckCrud() {
        String sub = "it-acct-sub-" + System.nanoTime();
        Login login = login(sub, "payer-" + sub + "@example.com", "Payer");

        // --- Repeated login returns the same account --------------------------
        JsonNode account = It.tree(given()
                .header("Authorization", "Bearer " + login.jwt())
                .when().get("/account")
                .then()
                .statusCode(200)
                .extract().asString());
        It.assertFieldOrder(account, List.of("accountID", "displayName", "deckIDs", "createdAt"));
        assertEquals(login.account().get("accountID").asText(), account.get("accountID").asText());

        // --- Unauthenticated access is rejected --------------------------------
        given().when().get("/account").then().statusCode(401);

        // --- Display-name update ----------------------------------------------
        given()
                .contentType("application/json")
                .body("{\"displayName\": \"Renamed Payer\"}")
                .header("Authorization", "Bearer " + login.jwt())
                .when().patch("/account")
                .then()
                .statusCode(200);
        assertEquals("Renamed Payer", It.tree(given()
                .header("Authorization", "Bearer " + login.jwt())
                .when().get("/account")
                .then()
                .statusCode(200)
                .extract().asString()).get("displayName").asText());

        // --- Deck CRUD ----------------------------------------------------------
        String location = given()
                .contentType("application/json")
                .body("{\"name\": \"It Deck\", \"description\": \"\", \"cardIds\": [\"card-a\", \"card-b\"]}")
                .header("Authorization", "Bearer " + login.jwt())
                .when().post("/account/decks")
                .then()
                .statusCode(201)
                .extract().header("Location");
        assertTrue(location.contains("/account/decks/"), location);
        String deckID = location.substring(location.lastIndexOf('/') + 1);

        JsonNode decks = It.tree(given()
                .header("Authorization", "Bearer " + login.jwt())
                .when().get("/account/decks")
                .then()
                .statusCode(200)
                .extract().asString());
        JsonNode deck = null;
        for (JsonNode candidate : decks) {
            It.assertFieldOrder(candidate, List.of(
                    "deckID", "accountID", "name", "description", "cardIds", "createdAt", "updatedAt"));
            if (deckID.equals(candidate.get("deckID").asText())) {
                deck = candidate;
            }
        }
        assertEquals("It Deck", deck.get("name").asText());
        assertEquals(2, deck.get("cardIds").size());

        JsonNode one = It.tree(given()
                .header("Authorization", "Bearer " + login.jwt())
                .when().get("/account/decks/{deckID}", deckID)
                .then()
                .statusCode(200)
                .extract().asString());
        assertEquals("It Deck", one.get("name").asText());

        given()
                .contentType("application/json")
                .body("{\"name\": \"It Deck 2\", \"description\": \"updated\", \"cardIds\": [\"card-c\"]}")
                .header("Authorization", "Bearer " + login.jwt())
                .when().put("/account/decks/{deckID}", deckID)
                .then()
                .statusCode(200);
        JsonNode updated = It.tree(given()
                .header("Authorization", "Bearer " + login.jwt())
                .when().get("/account/decks/{deckID}", deckID)
                .then()
                .statusCode(200)
                .extract().asString());
        assertEquals("It Deck 2", updated.get("name").asText());
        assertNotEquals(one.get("updatedAt").asText(), updated.get("updatedAt").asText());

        given()
                .header("Authorization", "Bearer " + login.jwt())
                .when().delete("/account/decks/{deckID}", deckID)
                .then()
                .statusCode(204);
        given()
                .header("Authorization", "Bearer " + login.jwt())
                .when().get("/account/decks/{deckID}", deckID)
                .then()
                .statusCode(404);

        // --- Logout revokes the bearer token ------------------------------------
        given()
                .header("Authorization", "Bearer " + login.jwt())
                .when().post("/account/logout")
                .then()
                .statusCode(204);
        given()
                .header("Authorization", "Bearer " + login.jwt())
                .when().get("/account")
                .then()
                .statusCode(401);
    }

    /** Admin emails (see %test config) receive a JWT with the admin role. */
    static String loginAdmin() {
        String sub = "it-admin-sub";
        JsonNode response = It.tree(given()
                .contentType("application/json")
                .body("{\"idToken\": \"%s\"}".formatted(
                        It.devIdToken(sub, "it-admin@example.com", "IT Admin")))
                .when().post("/account/login")
                .then()
                .statusCode(200)
                .extract().asString());
        return response.get("jwt").asText();
    }
}
