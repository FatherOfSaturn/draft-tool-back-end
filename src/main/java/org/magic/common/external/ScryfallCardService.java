package org.magic.common.external;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.api.scryfall.ScryfallListResponse;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service layer wrapping the Scryfall REST client. Handles paginated card search
 * (automatically following up to {@code scryfall.max-search-pages} pages) with
 * retry and fallback logic, and single-card lookup by ID.
 */
@ApplicationScoped
public class ScryfallCardService {

    private static final Logger LOGGER = LogManager.getLogger(ScryfallCardService.class);
    private static final Pattern PAGE_PATTERN = Pattern.compile("[?&]page=(\\d+)");

    private final ScryfallService scryfallService;
    private final int maxPages;

    @Inject
    public ScryfallCardService(@RestClient final ScryfallService scryfallService,
                               @ConfigProperty(name = "scryfall.max-search-pages", defaultValue = "5")
                               final int maxPages) {
        this.scryfallService = scryfallService;
        this.maxPages = maxPages;
    }

    public Uni<List<ScryfallCard>> searchAllCards(final String query) {
        return searchAllCards(query, "name", "auto", "cards");
    }

    /**
     * Searches for all cards matching the query, automatically paginating through
     * multiple Scryfall API pages until no more results remain or the page limit is reached.
     * Retries failed requests up to 2 times before falling back to an empty result.
     *
     * @param query the Scryfall search query
     * @param order sort order (default: "name")
     * @param dir   sort direction (default: "auto")
     * @param unique deduplication mode (default: "cards")
     * @return a {@link Uni} emitting the accumulated list of matching cards across all pages
     */
    public Uni<List<ScryfallCard>> searchAllCards(final String query, final String order,
                                                  final String dir, final String unique) {
        return fetchPage(query, order, dir, unique, 1, new ArrayList<>());
    }

    private Uni<List<ScryfallCard>> fetchPage(final String query, final String order,
                                               final String dir, final String unique,
                                               final int page, final List<ScryfallCard> accumulated) {
        if (page > maxPages) {
            LOGGER.warn("Reached max search pages ({}) for query: {}", maxPages, query);
            return Uni.createFrom().item(accumulated);
        }

        return scryfallService.searchCards(query, order, dir, unique, page, false, false, false)
            .onFailure().invoke(e -> LOGGER.error("Scryfall search failed (page {}): {}", page, e.getMessage()))
            .onFailure().retry().atMost(2)
            .onFailure().recoverWithItem(ScryfallListResponse.empty())
            .flatMap(response -> {
                accumulated.addAll(response.data());
                if (Boolean.TRUE.equals(response.hasMore()) && response.nextPage() != null) {
                    int nextPage = extractPageNumber(response.nextPage());
                    return fetchPage(query, order, dir, unique, nextPage, accumulated);
                }
                return Uni.createFrom().item(accumulated);
            });
    }

    private static int extractPageNumber(final String nextPage) {
        Matcher matcher = PAGE_PATTERN.matcher(nextPage);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    public Uni<ScryfallCard> getCardById(final String scryfallId) {
        return scryfallService.getCardById(scryfallId);
    }
}
