package org.magic.draft.util;

import io.smallrye.mutiny.Uni;
import jakarta.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("")
public interface GenericRestService {
    @Path("")
    @GET
    Uni<JsonObject> callWithDynmamicUrl();
}