package org.magic.pyramidDraft.external;

import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.magic.pyramidDraft.api.card.Cube;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@RegisterRestClient(configKey = "cubeCobraHostName")
public interface CubeCobraService {

    @GET
    @Path("/cube/api/cubeJSON/{cubeID}")
    public Uni<Cube> getCubeDataAsJson(@PathParam("cubeID") String cubeID);

    @GET
    @Path("/cubes")
    Uni<List<Cube>> getCubeDataAsJsonByOwner(@QueryParam("devCubeOwner") String owner);

}