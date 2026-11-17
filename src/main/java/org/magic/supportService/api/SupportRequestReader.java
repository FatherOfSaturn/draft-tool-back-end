package org.magic.supportService.api;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;

/**
 * Strict JSON reader for {@link CreateSupportRequest}.
 *
 * <p>Quarkus configures its shared {@link ObjectMapper} to ignore unknown
 * properties, which RESTEasy Reactive's built-in Jackson reader inherits — so
 * unknown fields sent to {@code POST /support} would otherwise be silently
 * dropped instead of rejected. This reader deserializes the body with a
 * derived mapper that enables {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES},
 * scoped to this one DTO so the REST clients and other endpoints are unaffected.
 * Because fields are bound only to the record's typed components, values such
 * as {@code {"$ne": null}} cannot reach MongoDB.</p>
 */
@Provider
@Consumes(MediaType.APPLICATION_JSON)
public class SupportRequestReader implements MessageBodyReader<CreateSupportRequest> {

    private final ObjectMapper strictMapper;

    @Inject
    public SupportRequestReader(final ObjectMapper objectMapper) {
        this.strictMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public boolean isReadable(final Class<?> type, final Type genericType,
                              final Annotation[] annotations, final MediaType mediaType) {
        return CreateSupportRequest.class.equals(type);
    }

    @Override
    public CreateSupportRequest readFrom(final Class<CreateSupportRequest> type, final Type genericType,
                                         final Annotation[] annotations, final MediaType mediaType,
                                         final MultivaluedMap<String, String> httpHeaders,
                                         final InputStream entityStream)
            throws IOException, WebApplicationException {
        try {
            return strictMapper.readValue(entityStream, CreateSupportRequest.class);
        } catch (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException e) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                    .entity(new org.magic.common.api.ErrorMessage(
                            "Unknown field in request body: " + e.getPropertyName()))
                    .build());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BadRequestException(e.getOriginalMessage());
        }
    }
}
