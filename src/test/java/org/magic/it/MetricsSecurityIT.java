package org.magic.it;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies the metrics endpoint is not anonymously readable: /q/metrics leaks
 * request paths, status codes and traffic volume, so it is restricted to the
 * admin role. The health endpoint stays public for probes.
 */
@QuarkusTest
@Tag("mongo")
class MetricsSecurityIT {

    @Test
    void anonymousCallerCannotReadMetrics() {
        int status = given()
                .redirects().follow(false)
                .when().get("/q/metrics")
                .then()
                .extract().statusCode();

        org.junit.jupiter.api.Assertions.assertTrue(
                status == 401 || status == 403,
                "expected /q/metrics to reject anonymous access, got " + status);
    }

    @Test
    void nonAdminUserCannotReadMetrics() {
        String userJwt = AccountApiIT.loginUser();

        int status = given()
                .header("Authorization", "Bearer " + userJwt)
                .redirects().follow(false)
                .when().get("/q/metrics")
                .then()
                .extract().statusCode();

        org.junit.jupiter.api.Assertions.assertEquals(403, status,
                "a non-admin user must not read metrics");
    }

    @Test
    void adminCanReadMetrics() {
        String adminJwt = AccountApiIT.loginAdmin();

        given()
                .header("Authorization", "Bearer " + adminJwt)
                .when().get("/q/metrics")
                .then()
                .statusCode(200);
    }

    @Test
    void healthEndpointRemainsPublic() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200);
    }
}
