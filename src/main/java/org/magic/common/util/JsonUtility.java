package org.magic.common.util;

import java.io.IOException;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.inject.Singleton;

/**
 * Thread-safe singleton utility for Jackson JSON serialization and deserialization.
 * Provides a shared {@link ObjectMapper} configured with pretty-printing, Java 8 time
 * support, and lenient unknown property handling. Can be used as a CDI bean or via
 * the static {@link #getInstance()} accessor.
 */
@Singleton
public final class JsonUtility {

    private static final Logger LOGGER = LogManager.getLogger(JsonUtility.class);
    private static final Object LOCK = new Object();

    private static volatile JsonUtility instance = null;

    private final ObjectMapper mapper;

    public JsonUtility() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper.registerModule(new JavaTimeModule());
    }

    public JsonUtility(final ObjectMapper mapper) {
        this.mapper = Objects.requireNonNullElseGet(mapper, () -> new ObjectMapper());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper.registerModule(new JavaTimeModule());
    }

    public static void initialize(final ObjectMapper mapper) {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
        instance = new JsonUtility(mapper);
    }

    /**
     * Returns the singleton instance, creating one with a default ObjectMapper if needed.
     * Uses double-checked locking for thread safety.
     */
    public static JsonUtility getInstance() {
        JsonUtility temp = instance;
        if (temp == null) {
            synchronized (LOCK) {
                temp = instance;
                if (temp == null) {
                    temp = new JsonUtility(null);
                    instance = temp;
                }
            }
        }
        return temp;
    }

    public static ObjectMapper map() {
        return getInstance().getMapperInstance();
    }

    static void clearInstance() {
        instance = null;
    }

    public ObjectMapper getMapperInstance() {
        return this.mapper;
    }

    /**
     * Deserializes a JSON string into an object of the given type.
     *
     * @param jsonString the JSON to parse
     * @param clazz      the target type
     * @return the deserialized object
     * @throws IOException if parsing fails
     */
    public <T> T fromJson(final String jsonString, final Class<? extends T> clazz) throws IOException {
        return mapper.readerFor(clazz)
                     .readValue(jsonString);
    }

    /**
     * Serializes an object to a pretty-printed JSON string.
     * Returns an empty string if serialization fails.
     *
     * @param type the object to serialize
     * @return the JSON string
     */
    public String toJson(final Object type) {

        try {
            return mapper.writeValueAsString(type);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to Serialize Object to Json.", e);
        }
        return "";
    }
}
