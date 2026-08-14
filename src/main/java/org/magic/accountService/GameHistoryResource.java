package org.magic.accountService;

import java.util.List;

import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.app.GameHistoryService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST resource exposing the authenticated user's cross-format game history.
 */
@Path("/account")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@Tag(name = "Account", description = "User accounts, authentication, and saved decks.")
public class GameHistoryResource {

    private final GameHistoryService gameHistoryService;

    public GameHistoryResource(final GameHistoryService gameHistoryService) {
        this.gameHistoryService = gameHistoryService;
    }

    /**
     * Fetches the unified game history for the authenticated caller, aggregated
     * across all draft types and sorted newest first.
     *
     * @param securityContext the caller's security context
     * @return the list of {@link GameHistoryEntry} objects
     */
    @GET
    @Path("/game/history")
    @Operation(summary = "Get game history",
            description = "Returns the authenticated caller's game history aggregated across all draft types, " +
                    "sorted newest first.")
    public Uni<List<GameHistoryEntry>> getGameHistory(@Context final SecurityContext securityContext) {
        return Uni.createFrom().item(() ->
                gameHistoryService.getGameHistory(securityContext.getUserPrincipal().getName()));
    }
}
