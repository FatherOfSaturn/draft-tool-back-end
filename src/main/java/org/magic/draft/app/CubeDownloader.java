package org.magic.draft.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.magic.draft.api.card.Cube;
import org.magic.draft.external.CubeCobraService;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CubeDownloader {
    private static final Logger LOGGER = LogManager.getLogger(CubeDownloader.class);

    @ConfigProperty(name = "quarkus.profile")
    String activeProfile;

    @ConfigProperty(name = "cubeOwner")
    String cubeOwner;

    @RestClient
    @Inject
    private CubeCobraService cubeCobraService;

    @Inject
    public CubeDownloader(@RestClient final CubeCobraService cubeCobraService) {
        this.cubeCobraService = cubeCobraService;
    }

    public Uni<Cube> getCubeForCubeID(final String cubeID) {
                LOGGER.info("profile: {}", activeProfile);
        switch (activeProfile) {
            case "dev" -> {
                LOGGER.info("owner: {}", cubeOwner);
                return cubeCobraService.getCubeDataAsJsonLOCAL(cubeOwner)
                                       .map(list -> list.getFirst());
            }
            case "prod" -> {
                return cubeCobraService.getCubeDataAsJson(cubeID)
                                       .onFailure().invoke(e -> LOGGER.error("Got error trying to download Cube Json. {}", e))
                                       .onFailure().retry().atMost(3);
            }
            case "gapped" -> {
                LOGGER.info("gapped Sowner: {}", cubeOwner);
                return cubeCobraService.getCubeDataAsJsonLOCAL(cubeOwner)
                                       .map(list -> list.getFirst());
            }
            default -> {
                return Uni.createFrom().failure(new Throwable("No cube download settings for env."));
            }
        }
    }
}