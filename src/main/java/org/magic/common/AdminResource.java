package org.magic.common;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.accountService.api.Account;
import org.magic.accountService.app.AccountDbHandler;
import org.magic.common.admin.AdminService;
import org.magic.common.api.admin.AdminCheckResponse;
import org.magic.common.api.admin.DonationStatsResponse;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.app.GameCoordination.GameCoordinationWorker;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for admin dashboard endpoints. Provides draft statistics,
 * donation summaries, admin status checks, and game cleanup operations.
 * All endpoints require the caller to be in the admin email whitelist.
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {
    private static final Logger LOGGER = LogManager.getLogger(AdminResource.class);

    private final AdminService adminService;
    private final AccountDbHandler accountDbHandler;
    private final GameCoordinationWorker gameWorker;

    @Inject
    public AdminResource(final AdminService adminService,
                         final AccountDbHandler accountDbHandler,
                         final GameCoordinationWorker gameWorker) {
        this.adminService = adminService;
        this.accountDbHandler = accountDbHandler;
        this.gameWorker = gameWorker;
    }

    /**
     * Returns draft statistics for a given draft type. Requires admin privileges.
     *
     * @param accountID the account ID of the caller (used to verify admin status)
     * @param draftType the type of draft to retrieve stats for (e.g. "pyramid", "sealed")
     * @return a JSON response containing the draft stat count, or 403 if not admin
     */
    @GET
    @Path("/{accountID}/stats/drafts/{draftType}")
    public Response getDraftStats(@PathParam("accountID") final String accountID,
                                  @PathParam("draftType") final String draftType) {
        if (!verifyAdmin(accountID)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        // TODO: Query database for draft statistics by draftType
        return Response.ok(50).build();
    }

    /**
     * Returns donation summary statistics including total donated
     * and current monthly donations. Requires admin privileges.
     *
     * @param accountID the account ID of the caller (used to verify admin status)
     * @return a JSON response containing {@link DonationStatsResponse}, or 403 if not admin
     */
    @GET
    @Path("/{accountID}/stats/donations")
    public Response getDonationSummary(@PathParam("accountID") final String accountID) {
        if (!verifyAdmin(accountID)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        // TODO: Query database for donation stats
        return Response.ok(new DonationStatsResponse(50, 40)).build();
    }

    /**
     * Checks whether a given account has admin privileges.
     *
     * @param accountID the account ID to check
     * @return a JSON response containing {@link AdminCheckResponse}
     */
    @GET
    @Path("/check/{accountID}")
    public Response getAdminStatus(@PathParam("accountID") final String accountID) {
        return Response.ok(new AdminCheckResponse(verifyAdmin(accountID))).build();
    }

    /**
     * Deletes all games matching the specified state. Requires admin privileges.
     *
     * @param accountID the account ID of the caller (used to verify admin status)
     * @param gameState the game state to filter by (e.g., "game_started", "game_complete")
     * @return a 200 OK response with the deletion count, or 403 if not admin
     */
    @DELETE
    @Path("/{accountID}/games/{gameState}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Response> deleteGamesWithStatus(@PathParam("accountID") final String accountID,
                                               @PathParam("gameState") final String gameState) {
        if (!verifyAdmin(accountID)) {
            return Uni.createFrom().item(Response.status(Response.Status.FORBIDDEN).build());
        }
        LOGGER.info("Admin deleting games with state: {}", gameState);
        return gameWorker.deleteGamesWithStatus(GameState.fromString(gameState));
    }

    /**
     * Looks up the account by ID and checks if its email is in the admin whitelist.
     *
     * @param accountID the account ID to verify
     * @return {@code true} if the account exists and its email is admin-whitelisted
     */
    private boolean verifyAdmin(final String accountID) {
        Optional<Account> account = accountDbHandler.findById(accountID);
        if (account.isEmpty()) {
            LOGGER.warn("Admin check failed: account not found: {}", accountID);
            return false;
        }
        boolean isAdmin = adminService.isAdmin(account.get().getEmail());
        if (!isAdmin) {
            LOGGER.warn("Admin check failed: {} is not in the admin whitelist", account.get().getEmail());
        }
        return isAdmin;
    }
}
