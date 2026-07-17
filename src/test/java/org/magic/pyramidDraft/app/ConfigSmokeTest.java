package org.magic.pyramidDraft.app;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ConfigSmokeTest {

    @Inject
    @ConfigProperty(name = "cubeOwner")
    String cubeOwner;

    @Inject
    @ConfigProperty(name = "quarkus.mongodb.database")
    String database;

    @Inject
    @ConfigProperty(name = "google.oauth.client-id", defaultValue = "")
    String clientId;

    @Test
    void yamlConfigLoadsCorrectly() {
        assertNotNull(cubeOwner, "cubeOwner not loaded from YAML");
        assertNotNull(database, "database not loaded from YAML");
        assertFalse(cubeOwner.isBlank(), "cubeOwner is blank");
        assertFalse(database.isBlank(), "database is blank");
    }

    @Test
    void googleClientIdLoadsInDevMode() {
        assertNotNull(clientId, "google.oauth.client-id not loaded from YAML");
        assertFalse(clientId.isBlank(), "google.oauth.client-id is blank");
    }
}
