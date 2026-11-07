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
        It.assertFieldOrder(created, List.of(
                "id", "title", "description", "contactEmail", "priority",
                "accountID", "status", "type", "createdOnDate", "lastStatusChangeDate"));
        assertEquals("NEW", created.get("status").asText());
        String id = created.get("id").asText();
        assertFalse(id.isBlank());

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
}
