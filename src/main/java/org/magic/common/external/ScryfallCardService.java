package org.magic.common.external;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.api.scryfall.ScryfallCollectionRequest;
import org.magic.common.api.scryfall.ScryfallCollectionResponse;
import org.magic.common.api.scryfall.ScryfallImageUris;
import org.magic.common.api.scryfall.ScryfallListResponse;
import org.magic.pyramidDraft.api.card.Card;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service layer wrapping the Scryfall REST client. Handles paginated card search
 * (automatically following up to {@code scryfall.max-search-pages} pages) with
 * retry and fallback logic, single-card lookup by ID, and batch lookup by name
 * via Scryfall's collection endpoint.
 */
@ApplicationScoped
public class ScryfallCardService {

    private static final Logger LOGGER = LogManager.getLogger(ScryfallCardService.class);
    private static final Pattern PAGE_PATTERN = Pattern.compile("[?&]page=(\\d+)");
    private static final int COLLECTION_BATCH_SIZE = 75;

    private final ScryfallService scryfallService;
    private final ScryfallCardCache cardCache;
    private final int maxPages;

    @Inject
    public ScryfallCardService(@RestClient final ScryfallService scryfallService,
                               @ConfigProperty(name = "scryfall.max-search-pages", defaultValue = "5")
                               final int maxPages,
                               final ScryfallCardCache cardCache) {
        this.scryfallService = scryfallService;
        this.cardCache = cardCache;
        this.maxPages = maxPages;
    }

    ScryfallCardService(final ScryfallService scryfallService, final int maxPages) {
        this(scryfallService, maxPages, null);
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
        if (cardCache != null && cardCache.isAvailable()) {
            ScryfallCard cached = cardCache.findById(scryfallId);
            if (cached != null) {
                return Uni.createFrom().item(cached);
            }
        }
        return scryfallService.getCardById(scryfallId);
    }

    /**
     * Converts a {@link ScryfallCard} into a CubeCobra {@link Card}.
     * For meld cards, fetches the meld result card to obtain the flip image.
     */
    public Uni<Card> toCubeCobraCard(final ScryfallCard card) {
        if (!"meld".equals(card.layout()) || card.allParts() == null) {
            return Uni.createFrom().item(Card.fromScryfallCard(card));
        }
        return card.allParts().stream()
                .filter(p -> "meld_result".equals(p.component()))
                .findFirst()
                .map(meldResult -> {
                    ScryfallImageUris meldImage = resolveMeldImageFromCache(meldResult.id());
                    if (meldImage != null) {
                        return Uni.createFrom().item(Card.fromScryfallCard(card, meldImage));
                    }
                    return scryfallService.getCardById(meldResult.id())
                            .map(meldCard -> Card.fromScryfallCard(card, meldCard != null ? meldCard.imageUris() : null))
                            .onFailure().invoke(e -> LOGGER.warn("Failed to fetch meld result {} for {}: {}",
                                    meldResult.id(), card.name(), e.getMessage()))
                            .onFailure().recoverWithItem(Card.fromScryfallCard(card));
                })
                .orElseGet(() -> Uni.createFrom().item(Card.fromScryfallCard(card)));
    }

    private ScryfallImageUris resolveMeldImageFromCache(final String meldResultId) {
        if (cardCache == null || !cardCache.isAvailable()) return null;
        ScryfallCard meldCard = cardCache.findById(meldResultId);
        return meldCard != null ? meldCard.imageUris() : null;
    }

    /**
     * Looks up a list of card names via Scryfall's collection endpoint, converting
     * results to CubeCobra {@link Card} definitions. Names are chunked into groups
     * of 75 (Scryfall's limit). Meld cards have their flip images resolved in a
     * second batched collection call.
     *
     * @param names the card names to look up
     * @return a {@link Uni} emitting a {@link BatchResult} with resolved cards and not-found names
     */
    public Uni<BatchResult> batchLookupByNames(final List<String> names) {
        if (names == null || names.isEmpty()) {
            return Uni.createFrom().item(new BatchResult(List.of(), List.of()));
        }

        List<String> filteredNames = names.stream()
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (filteredNames.isEmpty()) {
            return Uni.createFrom().item(new BatchResult(List.of(), List.of()));
        }

        if (cardCache != null && cardCache.isAvailable()) {
            return batchFromCache(filteredNames);
        }

        return batchFromScryfall(filteredNames);
    }

    private Uni<BatchResult> batchFromCache(final List<String> names) {
        List<ScryfallCard> cached = cardCache.findByNames(names);
        Set<String> cachedNames = cached.stream().map(ScryfallCard::name).collect(Collectors.toSet());
        List<String> missing = names.stream()
                .filter(n -> !cachedNames.contains(n))
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            LOGGER.debug("{} card(s) not found in cache, falling back to Scryfall API", missing.size());
            return batchFromScryfall(missing).flatMap(scryfallResult -> {
                List<Card> allCards = new ArrayList<>();
                allCards.addAll(convertCachedToCards(cached));
                allCards.addAll(scryfallResult.cards());
                List<String> allNotFound = new ArrayList<>(scryfallResult.notFound());
                return Uni.createFrom().item(new BatchResult(allCards, allNotFound));
            });
        }

        return convertCachedToCardsUni(cached)
                .map(cards -> new BatchResult(cards, List.of()));
    }

    private List<Card> convertCachedToCards(final List<ScryfallCard> cards) {
        Map<String, ScryfallImageUris> meldImages = new HashMap<>();
        if (cardCache != null && cardCache.isAvailable()) {
            for (ScryfallCard card : cards) {
                if ("meld".equals(card.layout()) && card.allParts() != null) {
                    card.allParts().stream()
                            .filter(p -> "meld_result".equals(p.component()))
                            .findFirst()
                            .ifPresent(part -> {
                                ScryfallCard meldResult = cardCache.findById(part.id());
                                if (meldResult != null && meldResult.imageUris() != null) {
                                    meldImages.put(card.id(), meldResult.imageUris());
                                }
                            });
                }
            }
        }
        return cards.stream()
                .map(card -> {
                    ScryfallImageUris meldImage = meldImages.get(card.id());
                    return meldImage != null ? Card.fromScryfallCard(card, meldImage) : Card.fromScryfallCard(card);
                })
                .collect(Collectors.toList());
    }

    private Uni<List<Card>> convertCachedToCardsUni(final List<ScryfallCard> cards) {
        return Uni.createFrom().item(convertCachedToCards(cards));
    }

    private Uni<BatchResult> batchFromScryfall(final List<String> names) {
        if (names.isEmpty()) {
            return Uni.createFrom().item(new BatchResult(List.of(), List.of()));
        }
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < names.size(); i += COLLECTION_BATCH_SIZE) {
            chunks.add(names.subList(i, Math.min(i + COLLECTION_BATCH_SIZE, names.size())));
        }

        List<ScryfallCard> allCards = new ArrayList<>();
        List<String> allNotFound = new ArrayList<>();

        return fetchChunks(chunks, 0, allCards, allNotFound)
                .flatMap(result -> resolveMeldImages(allCards, allNotFound));
    }

    private Uni<BatchResult> fetchChunks(final List<List<String>> chunks, final int index,
                                          final List<ScryfallCard> accumulatedCards,
                                          final List<String> accumulatedNotFound) {
        if (index >= chunks.size()) {
            return Uni.createFrom().item(new BatchResult(List.of(), accumulatedNotFound));
        }

        List<ScryfallCollectionRequest.ScryfallIdentifier> identifiers = chunks.get(index).stream()
                .map(ScryfallCollectionRequest.ScryfallIdentifier::new)
                .collect(Collectors.toList());

        return scryfallService.lookupByCollection(new ScryfallCollectionRequest(identifiers))
                .onFailure().invoke(e -> LOGGER.error("Scryfall collection lookup failed (chunk {}): {}", index, e.getMessage()))
                .onFailure().retry().atMost(2)
                .onFailure().recoverWithItem(new ScryfallCollectionResponse(List.of(), List.of()))
                .flatMap(response -> {
                    accumulatedCards.addAll(response.data());
                    if (response.notFound() != null) {
                        accumulatedNotFound.addAll(response.notFound().stream()
                                .map(ScryfallCollectionRequest.ScryfallIdentifier::name)
                                .collect(Collectors.toList()));
                    }
                    return fetchChunks(chunks, index + 1, accumulatedCards, accumulatedNotFound);
                });
    }

    private Uni<BatchResult> resolveMeldImages(final List<ScryfallCard> cards, final List<String> notFound) {
        List<String> meldResultIds = cards.stream()
                .filter(c -> "meld".equals(c.layout()) && c.allParts() != null)
                .flatMap(c -> c.allParts().stream())
                .filter(p -> "meld_result".equals(p.component()))
                .map(p -> p.id())
                .distinct()
                .collect(Collectors.toList());

        if (meldResultIds.isEmpty()) {
            return convertCards(cards, notFound);
        }

        List<ScryfallCollectionRequest.ScryfallIdentifier> identifiers = meldResultIds.stream()
                .map(id -> new ScryfallCollectionRequest.ScryfallIdentifier(id))
                .collect(Collectors.toList());

        return scryfallService.lookupByCollection(new ScryfallCollectionRequest(identifiers))
                .onFailure().invoke(e -> LOGGER.warn("Failed to fetch meld result images: {}", e.getMessage()))
                .onFailure().recoverWithItem(new ScryfallCollectionResponse(List.of(), List.of()))
                .flatMap(meldResponse -> {
                    Map<String, ScryfallImageUris> meldImages = new HashMap<>();
                    if (meldResponse.data() != null) {
                        for (ScryfallCard meldCard : meldResponse.data()) {
                            meldImages.put(meldCard.id(), meldCard.imageUris());
                        }
                    }
                    return convertCardsWithMeldImages(cards, notFound, meldImages);
                });
    }

    private Uni<BatchResult> convertCardsWithMeldImages(final List<ScryfallCard> cards,
                                                         final List<String> notFound,
                                                         final Map<String, ScryfallImageUris> meldImages) {
        List<Card> converted = cards.stream()
                .map(card -> {
                    if ("meld".equals(card.layout()) && card.allParts() != null) {
                        String meldResultId = card.allParts().stream()
                                .filter(p -> "meld_result".equals(p.component()))
                                .map(p -> p.id())
                                .findFirst()
                                .orElse(null);
                        if (meldResultId != null && meldImages.containsKey(meldResultId)) {
                            return Card.fromScryfallCard(card, meldImages.get(meldResultId));
                        }
                    }
                    return Card.fromScryfallCard(card);
                })
                .collect(Collectors.toList());
        return Uni.createFrom().item(new BatchResult(converted, notFound));
    }

    private Uni<BatchResult> convertCards(final List<ScryfallCard> cards, final List<String> notFound) {
        List<Card> converted = cards.stream()
                .map(Card::fromScryfallCard)
                .collect(Collectors.toList());
        return Uni.createFrom().item(new BatchResult(converted, notFound));
    }
}
