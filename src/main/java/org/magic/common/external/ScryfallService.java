package org.magic.common.external;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.api.scryfall.ScryfallListResponse;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

/**
 * MicroProfile REST client interface for the Scryfall API.
 * Provides endpoints for card search and card-by-ID lookup.
 * Configured via the {@code scryfallApi/mp-rest/url} property.
 */
@RegisterRestClient(configKey = "scryfallApi")
public interface ScryfallService {

    /**
     * Searches for cards matching the given query with pagination support.
     *
     * @param query              the Scryfall search query
     * @param order              sort order
     * @param dir                sort direction
     * @param unique             deduplication mode
     * @param page               the page number (1-indexed)
     * @param includeExtras      whether to include extra cards (tokens, etc.)
     * @param includeMultilingual whether to include non-English cards
     * @param includeVariations  whether to include variations
     * @return a {@link Uni} emitting the paginated search results
     */
    @GET
    @Path("/cards/search")
    Uni<ScryfallListResponse> searchCards(
        @QueryParam("q") String query,
        @QueryParam("order") @DefaultValue("name") String order,
        @QueryParam("dir") @DefaultValue("auto") String dir,
        @QueryParam("unique") @DefaultValue("cards") String unique,
        @QueryParam("page") @DefaultValue("1") Integer page,
        @QueryParam("include_extras") @DefaultValue("false") Boolean includeExtras,
        @QueryParam("include_multilingual") @DefaultValue("false") Boolean includeMultilingual,
        @QueryParam("include_variations") @DefaultValue("false") Boolean includeVariations
    );

    /**
     * Fetches a single card by its Scryfall ID.
     *
     * @param scryfallId the Scryfall card ID
     * @return a {@link Uni} emitting the card
     */
    @GET
    @Path("/cards/{id}")
    Uni<ScryfallCard> getCardById(@PathParam("id") String scryfallId);
}
