package org.magic.accountService;

import java.util.List;

import org.magic.accountService.api.GameHistoryEntry;
import org.magic.accountService.app.GameHistoryService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST resource exposing cross-format game history for an account.
 */
@Path("/account")
@Produces(MediaType.APPLICATION_JSON)
public class GameHistoryResource {

    private final GameHistoryService gameHistoryService;

    public GameHistoryResource(final GameHistoryService gameHistoryService) {
        this.gameHistoryService = gameHistoryService;
    }

    /**
     * Fetches the unified game history for an account, aggregated across all
     * draft types and sorted newest first.
     *
     * @param accountID the account to look up games for
     * @return the list of {@link GameHistoryEntry} objects
     */
    @GET
    @Path("/game/history/{accountID}")
    public Uni<List<GameHistoryEntry>> getGameHistory(@PathParam("accountID") final String accountID) {
        return Uni.createFrom().item(() -> gameHistoryService.getGameHistory(accountID));
    }
}
