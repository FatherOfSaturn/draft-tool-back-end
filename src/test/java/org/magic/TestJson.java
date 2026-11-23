package org.magic;

import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Lenient JSON mapper for tests, matching the configuration the application
 * (and the removed {@code JsonUtility}) used for fixtures: pretty-printed
 * output, unknown properties ignored, Java 8 time support.
 */
public final class TestJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    public static <T> T fromJson(final String json, final Class<? extends T> clazz) throws IOException {
        return MAPPER.readValue(json, clazz);
    }

    public static String toJson(final Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize value", e);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    private TestJson() {
    }
}
