package org.magic.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.magic.pyramidDraft.api.card.Cube;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.commons.io.IOUtils;

/**
 * Shared helpers for the API integration tests: loading cubes from fixtures,
 * crafting dev-mode Google ID tokens, and asserting the exact serialized JSON
 * shape of API responses (field names + field order + hidden PII).
 */
final class It {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    /** Parses a JSON string into a tree (field order is preserved). */
    static JsonNode tree(final String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new AssertionError("Body is not valid JSON: " + json, e);
        }
    }

    /** Loads one of the test-resource cube JSON files into a {@link Cube}. */
    static Cube cube(final String fileName) {
        InputStream is = It.class.getClassLoader().getResourceAsStream(fileName);
        if (is == null) {
            throw new IllegalStateException("Test resource not found: " + fileName);
        }
        try {
            String json = IOUtils.toString(is, StandardCharsets.UTF_8.name());
            return MAPPER.readValue(json, Cube.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse cube fixture " + fileName, e);
        }
    }

    /**
     * Crafts an unsigned ("alg:none") Google-style ID token, exactly like the
     * dev-mode {@code GoogleTokenVerifier} accepts (payload base64 decoded,
     * no signature check).
     */
    static String devIdToken(final String sub, final String email, final String name) {
        String payload = base64url("{" +
                "\"sub\":\"" + sub + "\"," +
                "\"email\":\"" + email + "\"," +
                "\"email_verified\":true," +
                "\"name\":\"" + name + "\"}");
        return base64url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + payload + ".";
    }

    /**
     * Returns the JSON field names of the given document in serialization order.
     * Fails (with a readable diff) if the order differs from {@code expected}.
     */
    static void assertFieldOrder(final JsonNode node, final List<String> expected) {
        List<String> actual = new ArrayList<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new AssertionError("JSON field order mismatch.\nexpected: " + expected
                    + "\nactual:   " + actual
                    + "\nbody:     " + node);
        }
    }

    static void assertFieldOrder(final String json, final List<String> expected) {
        assertFieldOrder(tree(json), expected);
    }

    private static String base64url(final String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private It() {
    }
}
