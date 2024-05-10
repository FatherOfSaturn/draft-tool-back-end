package org.magic.draft.app;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.magic.draft.api.card.Cube;
import org.magic.draft.external.CubeCobraService;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CubeDownloader {
    private static final Logger LOGGER = LogManager.getLogger(CubeDownloader.class);

    private final CubeCobraService cubeCobraService;

    @Inject
    public CubeDownloader(@RestClient final CubeCobraService cubeCobraService) {
        this.cubeCobraService = cubeCobraService;
    }

    public Uni<Cube> getCubeForCubeID(final String cubeID) {
        return cubeCobraService.getCubeDataAsJson(cubeID)
                               .onFailure().invoke(e -> LOGGER.error("Got error trying to download Cube Json. {}", e));
    }
}
