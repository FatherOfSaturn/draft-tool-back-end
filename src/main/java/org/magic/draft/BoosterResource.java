package org.magic.draft;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.card.CardPack;
import org.magic.draft.app.booster.BoosterWorker;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/booster")
public class BoosterResource {
    private static final Logger LOGGER = LogManager.getLogger(GameResource.class);

    final BoosterWorker boosterWorker;

    public BoosterResource(final BoosterWorker worker) {
        this.boosterWorker = worker;
    }

    @GET
    @Path("/cubeBoosters")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<List<CardPack>> makeBoostersForCube(final String cubeID,
                                                   final int numberOfPacks,
                                                   final int numberOfCardsInPack) {
        LOGGER.info("Call to make boosters for cube: {}", cubeID);
        return boosterWorker.makeDraftPacksFromCube(cubeID, numberOfPacks, numberOfCardsInPack);
    }
}