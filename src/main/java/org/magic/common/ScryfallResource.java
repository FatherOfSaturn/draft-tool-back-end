package org.magic.common;

import java.util.List;

import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.external.BatchResult;
import org.magic.common.external.ScryfallCardService;
import org.magic.pyramidDraft.api.card.Card;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource that exposes Scryfall API functionality through the backend.
 * Provides card search, card-by-ID lookup, batch lookup by name, and
 * CubeCobra-format conversion endpoints.
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
     * Searches for cards matching the query and returns the first result
     * as a CubeCobra {@link Card} definition.
     *
     * @param query the Scryfall search query (required)
     * @param order sort order (default: "name")
     * @param dir   sort direction (default: "auto")
     * @param unique deduplication mode (default: "cards")
     * @return a list containing at most one card in CubeCobra format, or 400 if {@code q} is missing
     */
    @GET
    @Path("/cards/search/cubecobra")
    public Uni<List<Card>> searchCardsAsCubeCobraDef(
            @QueryParam("q") final String query,
            @QueryParam("order") @DefaultValue("name") final String order,
            @QueryParam("dir") @DefaultValue("auto") final String dir,
            @QueryParam("unique") @DefaultValue("cards") final String unique) {
        if (query == null || query.isBlank()) {
            throw new WebApplicationException("Query parameter 'q' is required",
                    Response.status(Response.Status.BAD_REQUEST).build());
        }
        return scryfallCardService.searchAllCards(query, order, dir, unique)
                .flatMap(cards -> cards.isEmpty()
                        ? Uni.createFrom().item(List.of())
                        : scryfallCardService.toCubeCobraCard(cards.get(0))
                                .map(List::of));
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

    /**
     * Fetches a single card by its Scryfall ID and returns it as a CubeCobra
     * {@link Card} definition.
     *
     * @param scryfallId the Scryfall card ID
     * @return the card in CubeCobra format, or 404 if not found
     */
    @GET
    @Path("/cards/{id}/cubecobra")
    public Uni<Card> getCardByIdAsCubeCobraDef(@PathParam("id") final String scryfallId) {
        return scryfallCardService.getCardById(scryfallId)
                .flatMap(card -> {
                    if (card == null) {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                    return scryfallCardService.toCubeCobraCard(card);
                });
    }

    /**
     * Looks up multiple card names in a single batch via Scryfall's collection endpoint
     * and returns them as CubeCobra {@link Card} definitions.
     *
     * @param request the request body containing card names
     * @return resolved cards and a list of names that were not found
     */
    @POST
    @Path("/cards/batch/cubecobra")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<BatchResult> batchLookupAsCubeCobraDef(final BatchNamesRequest request) {
        if (request == null || request.names() == null || request.names().isEmpty()) {
            throw new WebApplicationException("Request body with 'names' list is required",
                    Response.status(Response.Status.BAD_REQUEST).build());
        }
        return scryfallCardService.batchLookupByNames(request.names());
    }

    /**
     * Request body for the batch card lookup endpoint.
     */
    public record BatchNamesRequest(@JsonProperty("names") List<String> names) {}
}
