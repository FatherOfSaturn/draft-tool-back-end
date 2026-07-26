package org.magic.supportService;

import java.util.List;

import org.magic.supportService.api.CreateSupportRequest;
import org.magic.supportService.api.SupportRequest;
import org.magic.supportService.api.SupportStatus;
import org.magic.supportService.api.UpdateSupportRequest;
import org.magic.supportService.app.SupportWorker;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for managing feature and support requests.
 * Provides standard CRUD operations as well as a dedicated status update endpoint.
 */
@Path("/support")
@Produces(MediaType.APPLICATION_JSON)
public class SupportResource {

    private final SupportWorker supportWorker;

    public SupportResource(final SupportWorker supportWorker) {
        this.supportWorker = supportWorker;
    }

    /**
     * Creates a new support request.
     *
     * @param request the creation payload
     * @return the created request with generated ID and timestamps
     */
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<SupportRequest> createRequest(final CreateSupportRequest request) {
        return supportWorker.createRequest(request);
    }

    /**
     * Retrieves a support request by its ID.
     *
     * @param id the request ID
     * @return the request, or 404 if not found
     */
    @GET
    @Path("/{id}")
    public Uni<SupportRequest> getRequest(@PathParam("id") final String id) {
        return supportWorker.getRequest(id)
                .map(req -> {
                    if (req == null) {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                    return req;
                });
    }

    /**
     * Lists all support requests, sorted by creation date descending.
     *
     * @return the list of all requests
     */
    @GET
    @Path("/")
    public Uni<List<SupportRequest>> getAllRequests() {
        return supportWorker.getAllRequests();
    }

    /**
     * Updates the mutable fields of an existing support request.
     *
     * @param id      the request ID
     * @param request the fields to update
     * @return the updated request, or 404 if not found
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<SupportRequest> updateRequest(@PathParam("id") final String id,
                                              final UpdateSupportRequest request) {
        return supportWorker.updateRequest(id, request)
                .map(req -> {
                    if (req == null) {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                    return req;
                });
    }

    /**
     * Updates only the status of a support request and records the change timestamp.
     *
     * @param id      the request ID
     * @param request the status update payload
     * @return the updated request, or 404 if not found
     */
    @PATCH
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<SupportRequest> updateStatus(@PathParam("id") final String id,
                                             final UpdateStatusRequest request) {
        return supportWorker.updateStatus(id, request.status())
                .map(req -> {
                    if (req == null) {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                    return req;
                });
    }

    /**
     * Permanently removes a support request from the database (admin/developer use).
     *
     * @param id the request ID
     * @return 204 No Content if deleted, 404 if not found
     */
    @DELETE
    @Path("/{id}")
    public Uni<Response> deleteRequest(@PathParam("id") final String id) {
        return supportWorker.deleteRequest(id)
                .map(deleted -> deleted ? Response.noContent().build()
                                        : Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Request body for the status update endpoint.
     */
    public record UpdateStatusRequest(@JsonProperty("status") SupportStatus status) {
        public UpdateStatusRequest {
            if (status == null) {
                throw new IllegalArgumentException("status is required");
            }
        }
    }
}
