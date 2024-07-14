package org.magic.draft.util;

import java.io.IOException;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.inject.Singleton;

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
    }

    public JsonUtility(final ObjectMapper mapper) {
        this.mapper = Objects.requireNonNullElseGet(mapper, () -> new ObjectMapper());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static void initialize(final ObjectMapper mapper) {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        instance = new JsonUtility(mapper);
    }

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

    public <T> T fromJson(final String jsonString, final Class<? extends T> clazz) throws IOException {
        return mapper.readerFor(clazz)
                     .readValue(jsonString);
    }

    public String toJson(final Object type) {

        try {
            return mapper.writeValueAsString(type);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to Serialize Object to Json.", e);
        }
        return "";
    }
}
