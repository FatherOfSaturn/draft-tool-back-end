package org.magic.pyramidDraft.external;

import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.magic.pyramidDraft.api.card.Cube;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

/**
 * MicroProfile REST client interface for the CubeCobra API.
 * Provides endpoints for fetching cube data by cube ID or owner name.
 * Configured via the {@code cubeCobraHostName/mp-rest/url} property.
 */
@RegisterRestClient(configKey = "cubeCobraHostName")
public interface CubeCobraService {

    /**
     * Fetches a cube's data by its ID from CubeCobra.
     *
     * @param cubeID the CubeCobra cube ID
     * @return a {@link Uni} emitting the {@link Cube}
     */
    @GET
    @Path("/cube/api/cubeJSON/{cubeID}")
    public Uni<Cube> getCubeDataAsJson(@PathParam("cubeID") String cubeID);

    /**
     * Fetches all cubes owned by the specified user.
     *
     * @param owner the CubeCobra owner name
     * @return a {@link Uni} emitting the list of cubes
     */
    @GET
    @Path("/cubes")
    Uni<List<Cube>> getCubeDataAsJsonByOwner(@QueryParam("devCubeOwner") String owner);

}