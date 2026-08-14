package org.magic.common;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.accountService.api.Account;
import org.magic.accountService.api.Deck;
import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.app.AccountDbHandler;
import org.magic.accountService.app.DeckDbHandler;
import org.magic.accountService.app.GameHistoryService;
import org.magic.common.api.admin.AdminCheckResponse;
import org.magic.common.api.admin.DonationStatsResponse;
import org.magic.common.api.admin.ScryfallCacheStatusResponse;
import org.magic.common.external.ScryfallBulkDataService;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.app.GameCoordination.GameCoordinationWorker;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST resource for admin dashboard endpoints. Provides draft statistics,
 * donation summaries, admin status checks, cross-account lookup, and game
 * cleanup operations. Access is enforced via the {@code admin} role embedded
 * in the caller's JWT at login (based on the {@code admin.emails} whitelist).
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
@Tag(name = "Admin", description = "Admin-only operations: stats, cross-account lookup, game cleanup, and Scryfall " +
        "cache management. All endpoints require the admin role.")
public class AdminResource {
    private static final Logger LOGGER = LogManager.getLogger(AdminResource.class);

    private final GameCoordinationWorker gameWorker;
    private final ScryfallBulkDataService bulkDataService;
    private final AccountDbHandler accountDbHandler;
    private final DeckDbHandler deckDbHandler;
    private final GameHistoryService gameHistoryService;

    @Inject
    public AdminResource(final GameCoordinationWorker gameWorker,
                         final ScryfallBulkDataService bulkDataService,
                         final AccountDbHandler accountDbHandler,
                         final DeckDbHandler deckDbHandler,
                         final GameHistoryService gameHistoryService) {
        this.gameWorker = gameWorker;
        this.bulkDataService = bulkDataService;
        this.accountDbHandler = accountDbHandler;
        this.deckDbHandler = deckDbHandler;
        this.gameHistoryService = gameHistoryService;
    }

    /**
     * Returns draft statistics for a given draft type.
     *
     * @param draftType the type of draft to retrieve stats for (e.g. "pyramid", "sealed")
     * @return a JSON response containing the draft stat count
     */
    @GET
    @Path("/stats/drafts/{draftType}")
    @Operation(summary = "Get draft stats",
            description = "Returns the draft game count for the given draft type.")
    public Response getDraftStats(@PathParam("draftType") final String draftType) {
        // TODO: Query database for draft statistics by draftType
        return Response.ok(50).build();
    }

    /**
     * Returns donation summary statistics including total donated
     * and current monthly donations.
     *
     * @return a JSON response containing {@link DonationStatsResponse}
     */
    @GET
    @Path("/stats/donations")
    @Operation(summary = "Get donation stats",
            description = "Returns donation summary statistics including total donated and current monthly donations.")
    public Response getDonationSummary() {
        // TODO: Query database for donation stats
        return Response.ok(new DonationStatsResponse(50, 40)).build();
    }

    /**
     * Confirms the caller has admin privileges (the endpoint is only reachable
     * by callers holding the admin role).
     *
     * @return a JSON response containing {@link AdminCheckResponse}
     */
    @GET
    @Path("/check")
    @Operation(summary = "Check admin access",
            description = "Confirms the caller holds the admin role; returns true since the endpoint is only " +
                    "reachable by admins.")
    public Response getAdminStatus() {
        return Response.ok(new AdminCheckResponse(true)).build();
    }

    /**
     * Looks up an account by its account ID. Admin only. The response omits
     * {@code email} and {@code googleSub} (both {@code @JsonIgnore}).
     *
     * @param accountID the account to look up
     * @return the account, or 404 if not found
     */
    @GET
    @Path("/accounts/{accountID}")
    @Operation(summary = "Get an account by ID",
            description = "Looks up an account by its account ID. PII fields (email, googleSub) are omitted from " +
                    "the response. Returns 404 if not found.")
    public Uni<Account> getAccountByID(@PathParam("accountID") @Size(max = 100) final String accountID) {
        return Uni.createFrom().item(() -> accountDbHandler.findById(accountID)
                .orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND)));
    }

    /**
     * Returns the aggregated game history for an arbitrary account across all
     * draft types. Admin only.
     *
     * @param accountID the account to look up
     * @return the entries, sorted by creation time descending
     */
    @GET
    @Path("/accounts/{accountID}/game/history")
    @Operation(summary = "Get an account's game history",
            description = "Returns the aggregated game history for an arbitrary account across all draft types, " +
                    "sorted newest first.")
    public Uni<List<GameHistoryEntry>> getGameHistoryByID(
            @PathParam("accountID") @Size(max = 100) final String accountID) {
        return Uni.createFrom().item(() -> gameHistoryService.getGameHistoryByAccountID(accountID));
    }

    /**
     * Returns all decks belonging to an arbitrary account. Admin only.
     *
     * @param accountID the account to look up
     * @return the list of decks
     */
    @GET
    @Path("/accounts/{accountID}/decks")
    @Operation(summary = "Get an account's decks",
            description = "Returns all decks belonging to the given account.")
    public Uni<List<Deck>> getDecksByID(@PathParam("accountID") @Size(max = 100) final String accountID) {
        return Uni.createFrom().item(() -> deckDbHandler.findByAccountID(accountID));
    }

    /**
     * Permanently deletes a deck belonging to an arbitrary account and removes
     * its ID from the account's deck list. Admin only.
     *
     * @param accountID the owning account
     * @param deckID    the deck to delete
     * @return 204 No Content if deleted, 404 if the deck was not found
     */
    @DELETE
    @Path("/accounts/{accountID}/decks/{deckID}")
    @Operation(summary = "Delete an account's deck",
            description = "Permanently deletes a deck belonging to the given account and removes its ID from the " +
                    "account's deck list. Returns 204 if deleted, 404 if the deck is missing or owned by another account.")
    public Uni<Response> deleteDeckByID(@PathParam("accountID") @Size(max = 100) final String accountID,
                                        @PathParam("deckID") @Size(max = 64) final String deckID) {
        return Uni.createFrom().item(() -> deckDbHandler.findById(deckID)
                .filter(deck -> accountID.equals(deck.getAccountID()))
                .map(deck -> {
                    deckDbHandler.deleteDeck(deckID);
                    accountDbHandler.removeDeckFromAccount(accountID, deckID);
                    return Response.noContent().build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build()));
    }

    /**
     * Deletes all games matching the specified state.
     *
     * @param gameState the game state to filter by (e.g., "game_started", "game_complete")
     * @return a 200 OK response with the deletion count
     */
    @DELETE
    @Path("/games/{gameState}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete games by state",
            description = "Deletes all games matching the given game state. Returns 400 for an invalid state.")
    public Uni<Response> deleteGamesWithStatus(@PathParam("gameState") final String gameState) {
        LOGGER.info("Admin deleting games with state: {}", gameState);
        return Uni.createFrom().item(() -> {
            try {
                return GameState.fromString(gameState);
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException("Invalid gameState: " + gameState,
                        Response.Status.BAD_REQUEST);
            }
        }).chain(gameWorker::deleteGamesWithStatus);
    }

    /**
     * Triggers an immediate refresh of the Scryfall bulk data cache.
     *
     * @return a JSON response with the import result
     */
    @POST
    @Path("/scryfall/cache/trigger")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Refresh the Scryfall cache",
            description = "Triggers an immediate refresh of the Scryfall bulk data cache and returns the import " +
                    "result, or 500 if the refresh fails.")
    public Response triggerCacheRefresh() {
        try {
            int count = bulkDataService.triggerRefresh();
            LOGGER.info("Admin triggered Scryfall cache refresh: {} cards imported", count);
            return Response.ok(new ScryfallCacheStatusResponse(
                    true, count, bulkDataService.getStatus().lastImportedAt(),
                    bulkDataService.getBulkDataType(), bulkDataService.isEnabled())).build();
        } catch (IllegalStateException | IOException e) {
            LOGGER.error("Admin-triggered cache refresh failed: {}", e.getMessage());
            return Response.serverError().entity("Cache refresh failed: " + e.getMessage()).build();
        }
    }

    /**
     * Returns the current status of the Scryfall bulk data cache.
     *
     * @return a JSON response with cache status
     */
    @GET
    @Path("/scryfall/cache/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get the Scryfall cache status",
            description = "Returns the current status of the Scryfall bulk data cache (availability, card count, " +
                    "last import time).")
    public Response getCacheStatus() {
        var status = bulkDataService.getStatus();
        return Response.ok(new ScryfallCacheStatusResponse(
                status.available(), status.cardCount(), status.lastImportedAt(),
                status.bulkDataType(), bulkDataService.isEnabled())).build();
    }
}
