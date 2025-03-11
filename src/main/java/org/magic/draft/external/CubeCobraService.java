package org.magic.draft.external;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.magic.draft.api.card.Cube;

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
    @Path("/cubes?devCubeOwner={owner}")
    public Uni<Cube> getCubeDataAsJsonLOCAL(@QueryParam("owner") String cubeID);    
}