package org.magic.common;

import java.util.List;

import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.external.BatchResult;
import org.magic.common.external.ScryfallCardService;
import org.magic.pyramidDraft.api.card.Card;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.smallrye.mutiny.Uni;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST resource that exposes Scryfall API functionality through the backend.
 * Provides card search, card-by-ID lookup, batch lookup by name, and
 * CubeCobra-format conversion endpoints.
 */
@Path("/scryfall")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Scryfall", description = "Card search and lookup proxied through the Scryfall API.")
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
    @Operation(summary = "Search cards",
            description = "Searches Scryfall for cards matching the query and returns all accumulated results. " +
                    "Returns 400 if the q parameter is missing.")
    public Uni<List<ScryfallCard>> searchCards(
            @QueryParam("q") @Size(max = 300) final String query,
            @QueryParam("order") @DefaultValue("name") @Size(max = 20) final String order,
            @QueryParam("dir") @DefaultValue("auto") @Size(max = 20) final String dir,
            @QueryParam("unique") @DefaultValue("cards") @Size(max = 20) final String unique) {
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
    @Operation(summary = "Search cards as CubeCobra definitions",
            description = "Searches Scryfall for cards and returns the first match converted to CubeCobra format. " +
                    "Returns 400 if the q parameter is missing.")
    public Uni<List<Card>> searchCardsAsCubeCobraDef(
            @QueryParam("q") @Size(max = 300) final String query,
            @QueryParam("order") @DefaultValue("name") @Size(max = 20) final String order,
            @QueryParam("dir") @DefaultValue("auto") @Size(max = 20) final String dir,
            @QueryParam("unique") @DefaultValue("cards") @Size(max = 20) final String unique) {
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
    @Operation(summary = "Get a card by ID",
            description = "Returns the card with the given Scryfall ID, or 404 if not found.")
    public Uni<ScryfallCard> getCardById(@PathParam("id") @Size(max = 64) final String scryfallId) {
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
    @Operation(summary = "Get a card as a CubeCobra definition",
            description = "Returns the card with the given Scryfall ID converted to CubeCobra format, or 404 if not found.")
    public Uni<Card> getCardByIdAsCubeCobraDef(@PathParam("id") @Size(max = 64) final String scryfallId) {
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
    @Operation(summary = "Batch lookup cards by name",
            description = "Looks up multiple card names in a single Scryfall collection request and returns them " +
                    "as CubeCobra definitions, along with any names that were not found. Returns 400 if the body " +
                    "is missing an non-empty names list.")
    public Uni<BatchResult> batchLookupAsCubeCobraDef(@Valid final BatchNamesRequest request) {
        if (request == null || request.names() == null || request.names().isEmpty()) {
            throw new WebApplicationException("Request body with 'names' list is required",
                    Response.status(Response.Status.BAD_REQUEST).build());
        }
        return scryfallCardService.batchLookupByNames(request.names());
    }

    /**
     * Request body for the batch card lookup endpoint.
     * The list is bounded to prevent cost amplification against the upstream API.
     */
    public record BatchNamesRequest(
            @JsonProperty("names") @Size(max = 300) java.util.List<@Size(max = 64) @jakarta.validation.constraints.NotBlank String> names) {}
}
