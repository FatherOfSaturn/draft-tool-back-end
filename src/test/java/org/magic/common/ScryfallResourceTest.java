package org.magic.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.external.BatchResult;
import org.magic.common.external.ScryfallCardService;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardDetails;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.WebApplicationException;

@ExtendWith(MockitoExtension.class)
class ScryfallResourceTest {

    @Mock
    ScryfallCardService scryfallCardService;

    ScryfallResource resource;

    private final CardDetails details = new CardDetails("set", "set_name", "scryfall_id", "img_small", "img_normal", null, "Bolt", "Instant", 1, List.of("R"));

    @BeforeEach
    void setup() {
        resource = new ScryfallResource(scryfallCardService);
    }

    @Test
    void shouldSearchCards() {
        var cards = List.of(mock(ScryfallCard.class));
        when(scryfallCardService.searchAllCards("Lightning Bolt", "name", "auto", "cards"))
                .thenReturn(Uni.createFrom().item(cards));

        List<ScryfallCard> result = resource.searchCards("Lightning Bolt", "name", "auto", "cards").await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void shouldThrowBadRequestForSearchCardsWithoutQuery() {
        assertThrows(WebApplicationException.class,
                () -> resource.searchCards(null, "name", "auto", "cards").await().indefinitely());
    }

    @Test
    void shouldThrowBadRequestForBlankQueryInSearchCards() {
        assertThrows(WebApplicationException.class,
                () -> resource.searchCards("   ", "name", "auto", "cards").await().indefinitely());
    }

    @Test
    void shouldSearchCardsAsCubeCobraDef() {
        var scryfallCards = List.of(mock(ScryfallCard.class));
        var cubeCard = new Card("Bolt", details, "id", 1, "Instant");

        when(scryfallCardService.searchAllCards("Bolt", "name", "auto", "cards"))
                .thenReturn(Uni.createFrom().item(scryfallCards));
        when(scryfallCardService.toCubeCobraCard(any())).thenReturn(Uni.createFrom().item(cubeCard));

        List<Card> result = resource.searchCardsAsCubeCobraDef("Bolt", "name", "auto", "cards").await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void shouldReturnEmptyListForCubeCobraSearchWithNoResults() {
        when(scryfallCardService.searchAllCards("NoMatch", "name", "auto", "cards"))
                .thenReturn(Uni.createFrom().item(List.of()));

        List<Card> result = resource.searchCardsAsCubeCobraDef("NoMatch", "name", "auto", "cards").await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowBadRequestForSearchCubeCobraWithoutQuery() {
        assertThrows(WebApplicationException.class,
                () -> resource.searchCardsAsCubeCobraDef(null, "name", "auto", "cards").await().indefinitely());
    }

    @Test
    void shouldGetCardByIdWhenFound() {
        var card = mock(ScryfallCard.class);
        when(scryfallCardService.getCardById("id-123")).thenReturn(Uni.createFrom().item(card));

        ScryfallCard result = resource.getCardById("id-123").await().indefinitely();

        assertNotNull(result);
    }

    @Test
    void shouldThrowNotFoundForMissingCardById() {
        when(scryfallCardService.getCardById("missing")).thenReturn(Uni.createFrom().item((ScryfallCard) null));

        assertThrows(WebApplicationException.class,
                () -> resource.getCardById("missing").await().indefinitely());
    }

    @Test
    void shouldGetCardByIdAsCubeCobraDefWhenFound() {
        when(scryfallCardService.getCardById("id-123")).thenReturn(Uni.createFrom().item(mock(ScryfallCard.class)));
        when(scryfallCardService.toCubeCobraCard(any())).thenReturn(Uni.createFrom().item(new Card("Bolt", details, "id", 1, "Instant")));

        Card result = resource.getCardByIdAsCubeCobraDef("id-123").await().indefinitely();

        assertNotNull(result);
    }

    @Test
    void shouldThrowNotFoundForMissingCardByIdAsCubeCobraDef() {
        when(scryfallCardService.getCardById("missing")).thenReturn(Uni.createFrom().item((ScryfallCard) null));

        assertThrows(WebApplicationException.class,
                () -> resource.getCardByIdAsCubeCobraDef("missing").await().indefinitely());
    }

    @Test
    void shouldBatchLookupAsCubeCobraDef() {
        var request = new ScryfallResource.BatchNamesRequest(List.of("Bolt", "Fire"));
        var batchResult = new BatchResult(List.of(new Card("Bolt", details, "id1", 1, "Instant"), new Card("Fire", details, "id2", 1, "Instant")), List.of());

        when(scryfallCardService.batchLookupByNames(List.of("Bolt", "Fire"))).thenReturn(Uni.createFrom().item(batchResult));

        BatchResult result = resource.batchLookupAsCubeCobraDef(request).await().indefinitely();

        assertEquals(2, result.cards().size());
    }

    @Test
    void shouldThrowBadRequestForBatchLookupWithNullRequest() {
        assertThrows(WebApplicationException.class,
                () -> resource.batchLookupAsCubeCobraDef(null).await().indefinitely());
    }

    @Test
    void shouldThrowBadRequestForBatchLookupWithEmptyNames() {
        var request = new ScryfallResource.BatchNamesRequest(List.of());

        assertThrows(WebApplicationException.class,
                () -> resource.batchLookupAsCubeCobraDef(request).await().indefinitely());
    }

    @Test
    void shouldThrowBadRequestForBatchLookupWithNullNames() {
        var request = new ScryfallResource.BatchNamesRequest(null);

        assertThrows(WebApplicationException.class,
                () -> resource.batchLookupAsCubeCobraDef(request).await().indefinitely());
    }

    @Test
    void shouldSearchCardsWithDefaultParameters() {
        var cards = List.of(mock(ScryfallCard.class));
        when(scryfallCardService.searchAllCards("Bolt", null, null, null))
                .thenReturn(Uni.createFrom().item(cards));

        List<ScryfallCard> result = resource.searchCards("Bolt", null, null, null).await().indefinitely();

        assertEquals(1, result.size());
    }
}
