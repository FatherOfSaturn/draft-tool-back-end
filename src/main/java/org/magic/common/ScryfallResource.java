package org.magic.common;

import java.util.List;

import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.external.ScryfallCardService;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource that exposes Scryfall API functionality through the backend.
 * Provides card search and card-by-ID lookup endpoints, delegating to
 * {@link ScryfallCardService} for pagination and retry handling.
 */
@Path("/scryfall")
@Produces(MediaType.APPLICATION_JSON)
public class ScryfallResource {

    private final ScryfallCardService scryfallCardService;

    public ScryfallResource(final ScryfallCardService scryfallCardService) {
        this.scryfallCardService = scryfallCardService;
    }

    /**
     * Searches for cards matching the query. All results across multiple pages
     * are accumulated and returned together.
     *
     * @param query the Scryfall search query (required)
     * @param order sort order (default: "name")
     * @param dir   sort direction (default: "auto")
     * @param unique deduplication mode (default: "cards")
     * @return a list of matching cards, or 400 if {@code q} is missing
     */
    @GET
    @Path("/cards/search")
    public Uni<List<ScryfallCard>> searchCards(
            @QueryParam("q") final String query,
            @QueryParam("order") @DefaultValue("name") final String order,
            @QueryParam("dir") @DefaultValue("auto") final String dir,
            @QueryParam("unique") @DefaultValue("cards") final String unique) {
        if (query == null || query.isBlank()) {
            throw new WebApplicationException("Query parameter 'q' is required",
                    Response.status(Response.Status.BAD_REQUEST).build());
        }
        return scryfallCardService.searchAllCards(query, order, dir, unique);
    }

    /**
     * Fetches a single card by its Scryfall ID.
     *
     * @param scryfallId the Scryfall card ID
     * @return the card, or 404 if not found
     */
    @GET
    @Path("/cards/{id}")
    public Uni<ScryfallCard> getCardById(@PathParam("id") final String scryfallId) {
        return scryfallCardService.getCardById(scryfallId)
                .map(card -> {
                    if (card == null) {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                    return card;
                });
    }
}
