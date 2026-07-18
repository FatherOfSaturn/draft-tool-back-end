package org.magic.pyramidDraft.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.magic.pyramidDraft.api.card.Cube;
import org.magic.pyramidDraft.external.CubeCobraService;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Downloads cube data from CubeCobra. In dev mode, fetches by owner name;
 * in prod mode, fetches by cube ID. Supports retry on failure in prod.
 */
@ApplicationScoped
public class CubeDownloader {
    private static final Logger LOGGER = LogManager.getLogger(CubeDownloader.class);

    @ConfigProperty(name = "quarkus.profile")
    String activeProfile;

    @ConfigProperty(name = "cubeOwner")
    String cubeOwner;

    private final CubeCobraService cubeCobraService;

    @Inject
    public CubeDownloader(@RestClient final CubeCobraService cubeCobraService) {
        this.cubeCobraService = cubeCobraService;
    }

    /**
     * Fetches cube data based on the active Quarkus profile.
     * In dev/gapped mode, uses the configured cube owner name; in prod, uses the cube ID directly.
     *
     * @param cubeID the cube ID to fetch (used in prod mode)
     * @return a {@link Uni} emitting the {@link Cube}
     */
    public Uni<Cube> getCubeForCubeID(final String cubeID) {
                LOGGER.info("profile: {}", activeProfile);
        switch (activeProfile) {
            case "dev" -> {
                LOGGER.info("owner: {}", cubeOwner);
                return cubeCobraService.getCubeDataAsJsonByOwner(cubeOwner)
                                       .map(list -> list.iterator().next());
            }
            case "prod" -> {
                return cubeCobraService.getCubeDataAsJson(cubeID)
                                       .onFailure().invoke(e -> LOGGER.error("Got error trying to download Cube Json. {}", e))
                                       .onFailure().retry().atMost(3);
            }
            case "gapped" -> {
                LOGGER.info("gapped Sowner: {}", cubeOwner);
                return cubeCobraService.getCubeDataAsJsonByOwner(cubeOwner)
                                       .map(list -> list.iterator().next());
            }
            default -> {
                return Uni.createFrom().failure(new Throwable("No cube download settings for env."));
            }
        }
    }
}