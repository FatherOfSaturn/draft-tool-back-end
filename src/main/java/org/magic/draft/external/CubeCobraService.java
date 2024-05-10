package org.magic.draft.external;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.magic.draft.api.card.Cube;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/cube/api/cubeJSON/")
@RegisterRestClient(configKey = "cubeCobraHostName")
public interface CubeCobraService {

    @GET
    @Path("/{cubeID}")
    public Uni<Cube> getCubeDataAsJson(@PathParam("cubeID") String cubeID);    
}