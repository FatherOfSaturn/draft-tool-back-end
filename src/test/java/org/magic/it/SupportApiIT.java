package org.magic.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end integration test for the support REST API against a real MongoDB.
 * Locks the serialized JSON wire shapes and verifies PII-free public views plus
 * the admin-only full view (admin JWT minted via the helper in {@code AccountApiIT}).
 */
@QuarkusTest
@Tag("mongo")
class SupportApiIT {

    private static final List<String> SUPPORT_ORDER = List.of(
            "id", "title", "description", "priority", "status",
            "type", "createdOnDate", "lastStatusChangeDate");

    @Test
    void publicFlowHidesPiiAndAdminEdits() {
        String suffix = Long.toHexString(System.nanoTime());
        JsonNode created = It.tree(given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "IT crash on merge %s",
                        "description": "Pyramid merge throws 500.",
                        "contactEmail": "it-user-%s@example.com",
                        "priority": "medium",
                        "type": "bug_fix"
                      }""".formatted(suffix, suffix))
                .when().post("/support")
                .then()
                .statusCode(200)
                .extract().asString());
        // The create response is a minimal ack: only the ticket ID, no content
        // or PII echo back to the anonymous caller.
        It.assertFieldOrder(created, List.of("id"));
        String id = created.get("id").asText();
        assertFalse(id.isBlank());

        JsonNode statusProbe = It.tree(given()
                .when().get("/support/public")
                .then()
                .statusCode(200)
                .extract().asString());
        String createdStatus = null;
        for (JsonNode entry : statusProbe) {
            if (entry.get("id").asText().equals(id)) {
                createdStatus = entry.get("status").asText();
            }
        }
        assertEquals("NEW", createdStatus);

        // --- Public view: PII-free --------------------------------------------
        String single = given()
                .when().get("/support/{id}", id)
                .then()
                .statusCode(200)
                .extract().asString();
        It.assertFieldOrder(single, SUPPORT_ORDER);
        assertFalse(single.contains("contactEmail"), "public view exposed contactEmail");
        assertFalse(single.contains("accountID"), "public view exposed accountID");

        JsonNode publicList = It.tree(given()
                .when().get("/support/public")
                .then()
                .statusCode(200)
                .extract().asString());
        assertTrue(publicList.size() >= 1);
        for (JsonNode entry : publicList) {
            It.assertFieldOrder(entry, List.of(
                    "id", "title", "description", "priority", "status",
                    "type", "createdOnDate", "lastStatusChangeDate"));
            assertFalse(entry.has("contactEmail"), "public list exposed contactEmail");
        }

        // --- Anonymous callers cannot list or mutate ---------------------------
        given().when().get("/support/").then().statusCode(401);

        // --- Admin: full list contains PII, then update + delete ----------------
        String adminJwt = AccountApiIT.loginAdmin();

        JsonNode adminList = It.tree(given()
                .header("Authorization", "Bearer " + adminJwt)
                .when().get("/support/")
                .then()
                .statusCode(200)
                .extract().asString());
        JsonNode mine = null;
        for (JsonNode entry : adminList) {
            It.assertFieldOrder(entry, List.of(
                    "id", "title", "description", "contactEmail", "priority",
                    "accountID", "status", "type", "createdOnDate", "lastStatusChangeDate"));
            if (id.equals(entry.get("id").asText())) {
                mine = entry;
            }
        }
        assertEquals("it-user-" + suffix + "@example.com", mine.get("contactEmail").asText());

        JsonNode statusUpdate = It.tree(given()
                .contentType("application/json")
                .body("{\"status\": \"in_progress\"}")
                .header("Authorization", "Bearer " + adminJwt)
                .when().patch("/support/{id}/status", id)
                .then()
                .statusCode(200)
                .extract().asString());
        It.assertFieldOrder(statusUpdate, List.of(
                "id", "title", "description", "contactEmail", "priority",
                "accountID", "status", "type", "createdOnDate", "lastStatusChangeDate"));
        assertEquals("IN_PROGRESS", statusUpdate.get("status").asText());

        JsonNode updated = It.tree(given()
                .contentType("application/json")
                .body("{\"priority\": \"low\"}")
                .header("Authorization", "Bearer " + adminJwt)
                .when().put("/support/{id}", id)
                .then()
                .statusCode(200)
                .extract().asString());
        assertEquals("LOW", updated.get("priority").asText());
        assertEquals("IN_PROGRESS", updated.get("status").asText());

        given()
                .header("Authorization", "Bearer " + adminJwt)
                .when().delete("/support/{id}", id)
                .then()
                .statusCode(204);
        given()
                .header("Authorization", "Bearer " + adminJwt)
                .when().get("/support/{id}", id)
                .then()
                .statusCode(404);
    }

    private static JsonNode adminSupportIds(final String adminJwt) {
        return It.tree(given()
                .header("Authorization", "Bearer " + adminJwt)
                .when().get("/support/")
                .then()
                .statusCode(200)
                .extract().asString());
    }

    private static boolean containsId(final JsonNode list, final String id) {
        for (JsonNode entry : list) {
            if (entry.get("id").asText().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void maliciousAndOversizedBodiesAreRejectedWithoutPersisting() {
        String adminJwt = AccountApiIT.loginAdmin();
        int before = adminSupportIds(adminJwt).size();

        // Mongo-operator-shaped payload: object where a string is expected.
        given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "injected",
                        "description": "attempt",
                        "contactEmail": {"$ne": null},
                        "priority": "medium",
                        "type": "bug_fix"
                      }""")
                .when().post("/support")
                .then()
                .statusCode(400);

        // Unknown fields are rejected.
        given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "injected",
                        "description": "attempt",
                        "contactEmail": "it-spam@example.com",
                        "priority": "medium",
                        "type": "bug_fix",
                        "evil": {"$gt": ""}
                      }""")
                .when().post("/support")
                .then()
                .statusCode(400);

        // Oversized fields are rejected.
        given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "%s",
                        "description": "attempt",
                        "contactEmail": "it-spam@example.com",
                        "priority": "medium",
                        "type": "bug_fix"
                      }""".formatted("x".repeat(201)))
                .when().post("/support")
                .then()
                .statusCode(400);

        given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "oversized",
                        "description": "%s",
                        "contactEmail": "it-spam@example.com",
                        "priority": "medium",
                        "type": "bug_fix"
                      }""".formatted("y".repeat(4001)))
                .when().post("/support")
                .then()
                .statusCode(400);

        // Invalid email format is rejected.
        given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "bad email",
                        "description": "attempt",
                        "contactEmail": "not-an-email",
                        "priority": "medium",
                        "type": "bug_fix"
                      }""")
                .when().post("/support")
                .then()
                .statusCode(400);

        // Invalid enum values are rejected.
        given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "bad enum",
                        "description": "attempt",
                        "contactEmail": "it-spam@example.com",
                        "priority": "ultra",
                        "type": "bug_fix"
                      }""")
                .when().post("/support")
                .then()
                .statusCode(400);

        assertEquals(before, adminSupportIds(adminJwt).size(), "rejected submissions must not persist");
    }

    @Test
    void honeypotSubmissionsLookSuccessfulButCreateNoTicket() {
        String adminJwt = AccountApiIT.loginAdmin();
        int before = adminSupportIds(adminJwt).size();

        JsonNode ack = It.tree(given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "IT honeypot %s",
                        "description": "Bot filled the hidden field.",
                        "contactEmail": "it-bot-%s@example.com",
                        "priority": "low",
                        "type": "misc_support",
                        "website": "http://spam.example"
                      }""".formatted(Long.toHexString(System.nanoTime()), Long.toHexString(System.nanoTime())))
                .when().post("/support")
                .then()
                .statusCode(200)
                .extract().asString());

        // Indistinguishable from a real success to the client...
        It.assertFieldOrder(ack, List.of("id"));
        String fakeId = ack.get("id").asText();
        assertFalse(fakeId.isBlank());

        // ...but nothing was persisted.
        assertEquals(before, adminSupportIds(adminJwt).size());
        given().when().get("/support/{id}", fakeId).then().statusCode(404);
    }

    @Test
    void accountIDIsNoLongerAcceptedFromAnonymousCallers() {
        given()
                .contentType("application/json")
                .body("""
                      {
                        "title": "spoofed attribution",
                        "description": "Should not be accepted.",
                        "contactEmail": "it-spoof@example.com",
                        "priority": "low",
                        "accountID": "507f1f77bcf86cd799439099",
                        "type": "bug_fix"
                      }""")
                .when().post("/support")
                .then()
                .statusCode(400);
    }
}
