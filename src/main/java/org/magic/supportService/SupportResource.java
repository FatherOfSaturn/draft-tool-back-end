package org.magic.supportService;

import java.util.List;

import org.magic.supportService.api.CreateSupportRequest;
import org.magic.supportService.api.PublicSupportRequest;
import org.magic.supportService.api.SupportRequest;
import org.magic.supportService.api.SupportStatus;
import org.magic.supportService.api.UpdateSupportRequest;
import org.magic.supportService.app.SupportWorker;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST resource for managing feature and support requests.
 * Provides standard CRUD operations as well as a dedicated status update endpoint.
 */
@Path("/support")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Support", description = "Feature and support request management. Creation and public views are " +
        "anonymous; mutation and admin list endpoints require the admin role.")
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
    @Operation(summary = "Create a support request",
            description = "Creates a new support request. Anonymous; rate-limited.")
    public Uni<SupportRequest> createRequest(@Valid final CreateSupportRequest request) {
        return supportWorker.createRequest(request);
    }

    /**
     * Retrieves a support request by its ID.
     * Public, but returns only the PII-free {@link PublicSupportRequest} view —
     * contact email and account ID are never exposed through this endpoint.
     *
     * @param id the request ID
     * @return the request, or 404 if not found
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Get a support request",
            description = "Returns the PII-free view of a single support request. Contact email and account ID " +
                    "are never exposed. Returns 404 if not found.")
    public Uni<PublicSupportRequest> getRequest(@PathParam("id") @Size(max = 64) final String id) {
        return supportWorker.getRequest(id)
                .map(req -> {
                    if (req == null) {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                    return PublicSupportRequest.from(req);
                });
    }

    /**
     * Lists all support requests, sorted by creation date descending.
     * Admin only.
     *
     * @return the list of all requests
     */
    @GET
    @Path("/")
    @RolesAllowed("admin")
    @Operation(summary = "List all support requests",
            description = "Returns all support requests including PII fields. Admin only.")
    public Uni<List<SupportRequest>> getAllRequests() {
        return supportWorker.getAllRequests();
    }

    /**
     * Lists the public, PII-free view of all support requests, sorted by creation
     * date descending. Contact email and account ID are never exposed. Deleted
     * requests are excluded.
     *
     * @return the list of public requests
     */
    @GET
    @Path("/public")
    @Operation(summary = "List public support requests",
            description = "Returns the PII-free view of all non-deleted support requests. Anonymous.")
    public Uni<List<PublicSupportRequest>> getPublicRequests() {
        return supportWorker.getAllRequests()
                .map(requests -> requests.stream()
                        .filter(req -> req.getStatus() != SupportStatus.DELETED)
                        .map(PublicSupportRequest::from)
                        .toList());
    }

    /**
     * Updates the mutable fields of an existing support request.
     * Admin only.
     *
     * @param id      the request ID
     * @param request the fields to update
     * @return the updated request, or 404 if not found
     */
    @PUT
    @Path("/{id}")
    @RolesAllowed("admin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a support request",
            description = "Updates the mutable fields of a support request. Admin only. Returns 404 if not found.")
    public Uni<SupportRequest> updateRequest(@PathParam("id") @Size(max = 64) final String id,
                                              @Valid final UpdateSupportRequest request) {
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
     * Admin only.
     *
     * @param id      the request ID
     * @param request the status update payload
     * @return the updated request, or 404 if not found
     */
    @PATCH
    @Path("/{id}/status")
    @RolesAllowed("admin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a request status",
            description = "Updates only the status of a support request and records the change timestamp. " +
                    "Admin only. Returns 404 if not found.")
    public Uni<SupportRequest> updateStatus(@PathParam("id") @Size(max = 64) final String id,
                                             @Valid final UpdateStatusRequest request) {
        return supportWorker.updateStatus(id, request.status())
                .map(req -> {
                    if (req == null) {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                    return req;
                });
    }

    /**
     * Permanently removes a support request from the database. Admin only.
     *
     * @param id the request ID
     * @return 204 No Content if deleted, 404 if not found
     */
    @DELETE
    @Path("/{id}")
    @RolesAllowed("admin")
    @Operation(summary = "Delete a support request",
            description = "Permanently removes a support request. Admin only. Returns 204 if deleted, 404 if not found.")
    public Uni<Response> deleteRequest(@PathParam("id") @Size(max = 64) final String id) {
        return supportWorker.deleteRequest(id)
                .map(deleted -> deleted ? Response.noContent().build()
                                        : Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Request body for the status update endpoint.
     */
    public record UpdateStatusRequest(@JsonProperty("status") @NotNull SupportStatus status) {
        public UpdateStatusRequest {
            if (status == null) {
                throw new IllegalArgumentException("status is required");
            }
        }
    }
}
