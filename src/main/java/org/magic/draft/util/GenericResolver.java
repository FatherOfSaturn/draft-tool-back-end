package org.magic.draft.util;

import java.io.IOException;
import java.net.URI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@RegisterRestClient(configKey = "scryfallUrl")
public class GenericResolver {

    private static final Logger LOGGER = LogManager.getLogger(GenericResolver.class);
    final GenericRestService resolver;


    @Inject
    public GenericResolver(@RestClient final GenericRestService resolver) {
        this.resolver = resolver;
    }

    public <T> Uni<T> invokeUrl(String url, Class<T> expectedResponseType) {
        URI anyDynamicUrl = URI.create(url);
        GenericRestService simpleGetApi = RestClientBuilder.newBuilder().baseUri(anyDynamicUrl)
                .build(GenericRestService.class);
                
        return simpleGetApi.callWithDynmamicUrl()
                           .onItem().transformToUni(jsonResponse -> {
                                try {
                                    return Uni.createFrom().item(
                                        JsonUtility.getInstance().fromJson(jsonResponse, expectedResponseType)
                                );
                                } catch (IOException e) {
                                    return Uni.createFrom().failure(e);
                                }
                           })
                           .onFailure().invoke(err -> LOGGER.error("Error with Generic Rest Call", err));
    }
}