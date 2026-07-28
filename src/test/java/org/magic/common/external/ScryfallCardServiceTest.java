package org.magic.common.external;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.api.scryfall.ScryfallCollectionRequest;
import org.magic.common.api.scryfall.ScryfallCollectionResponse;
import org.magic.common.api.scryfall.ScryfallImageUris;
import org.magic.common.api.scryfall.ScryfallListResponse;
import org.magic.common.api.scryfall.ScryfallRelatedCard;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class ScryfallCardServiceTest {

    @Mock
    ScryfallService scryfallService;

    ScryfallCardService cardService;

    @BeforeEach
    void setup() {
        cardService = new ScryfallCardService(scryfallService, 5);
    }

    @Test
    void shouldSearchAllCardsSinglePage() {
        var response = new ScryfallListResponse("list", List.of(mock(ScryfallCard.class), mock(ScryfallCard.class)), false, null, null, null);

        when(scryfallService.searchCards(anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Uni.createFrom().item(response));

        List<ScryfallCard> result = cardService.searchAllCards("Bolt").await().indefinitely();

        assertEquals(2, result.size());
    }

    @Test
    void shouldPaginateThroughMultiplePages() {
        var page1 = new ScryfallListResponse("list", List.of(mock(ScryfallCard.class)), true, "https://api.scryfall.com/cards/search?page=2", null, null);
        var page2 = new ScryfallListResponse("list", List.of(mock(ScryfallCard.class)), false, null, null, null);

        when(scryfallService.searchCards(anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Uni.createFrom().item(page1))
                .thenReturn(Uni.createFrom().item(page2));

        List<ScryfallCard> result = cardService.searchAllCards("Bolt").await().indefinitely();

        assertEquals(2, result.size());
    }

    @Test
    void shouldHandleSearchFailureWithFallback() {
        when(scryfallService.searchCards(anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("API error")));

        List<ScryfallCard> result = cardService.searchAllCards("Bolt").await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldStopAtMaxPages() {
        var page = new ScryfallListResponse("list", List.of(mock(ScryfallCard.class)), false, null, null, null);

        when(scryfallService.searchCards(anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Uni.createFrom().item(page));

        List<ScryfallCard> result = cardService.searchAllCards("Bolt").await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetCardById() {
        when(scryfallService.getCardById("id-123")).thenReturn(Uni.createFrom().item(mock(ScryfallCard.class)));

        ScryfallCard result = cardService.getCardById("id-123").await().indefinitely();

        assertNotNull(result);
    }

    @Test
    void shouldConvertNonMeldCardToCubeCobraCard() {
        var scryfallCard = mock(ScryfallCard.class);
        when(scryfallCard.id()).thenReturn("id");
        when(scryfallCard.name()).thenReturn("Bolt");
        when(scryfallCard.layout()).thenReturn("normal");
        when(scryfallCard.set()).thenReturn("set");
        when(scryfallCard.setName()).thenReturn("Set");
        when(scryfallCard.typeLine()).thenReturn("Instant");
        when(scryfallCard.cmc()).thenReturn(1.0);
        when(scryfallCard.manaCost()).thenReturn("{R}");
        when(scryfallCard.imageUris()).thenReturn(new ScryfallImageUris("s.jpg", "n.jpg", "l.jpg", "p.png", "a.png", "b.png"));

        var result = cardService.toCubeCobraCard(scryfallCard);

        assertNotNull(result.await().indefinitely());
        assertEquals("Bolt", result.await().indefinitely().getName());
    }

    @Test
    void shouldReturnEmptyBatchResultForNullNames() {
        BatchResult result = cardService.batchLookupByNames(null).await().indefinitely();

        assertTrue(result.cards().isEmpty());
        assertTrue(result.notFound().isEmpty());
    }

    @Test
    void shouldReturnEmptyBatchResultForEmptyNames() {
        BatchResult result = cardService.batchLookupByNames(List.of()).await().indefinitely();

        assertTrue(result.cards().isEmpty());
        assertTrue(result.notFound().isEmpty());
    }

    @Test
    void shouldFilterOutBlankNamesInBatchLookup() {
        BatchResult result = cardService.batchLookupByNames(Arrays.asList("", null, "  ")).await().indefinitely();

        assertTrue(result.cards().isEmpty());
        assertTrue(result.notFound().isEmpty());
    }

    @Test
    void shouldBatchLookupByNames() {
        var card = mock(ScryfallCard.class);
        when(card.id()).thenReturn("id");
        when(card.name()).thenReturn("Test Card");
        when(card.layout()).thenReturn("normal");
        when(card.set()).thenReturn("set");
        when(card.setName()).thenReturn("Set");
        when(card.typeLine()).thenReturn("Instant");
        when(card.cmc()).thenReturn(1.0);
        when(card.manaCost()).thenReturn("{R}");
        when(card.imageUris()).thenReturn(new ScryfallImageUris("s.jpg", "n.jpg", "l.jpg", "p.png", "a.png", "b.png"));

        var response = new ScryfallCollectionResponse(List.of(card), List.of());

        when(scryfallService.lookupByCollection(any())).thenReturn(Uni.createFrom().item(response));

        BatchResult result = cardService.batchLookupByNames(List.of("Test Card")).await().indefinitely();

        assertEquals(1, result.cards().size());
        assertTrue(result.notFound().isEmpty());
    }

    @Test
    void shouldHandleCollectionLookupFailure() {
        when(scryfallService.lookupByCollection(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("API error")));

        BatchResult result = cardService.batchLookupByNames(List.of("Test Card")).await().indefinitely();

        assertTrue(result.cards().isEmpty());
    }

    @Test
    void shouldIncludeNotFoundNames() {
        var notFound = List.of(new ScryfallCollectionRequest.ScryfallIdentifier("Missing Card"));
        var response = new ScryfallCollectionResponse(List.of(), notFound);

        when(scryfallService.lookupByCollection(any())).thenReturn(Uni.createFrom().item(response));

        BatchResult result = cardService.batchLookupByNames(List.of("Missing Card")).await().indefinitely();

        assertTrue(result.cards().isEmpty());
        assertEquals(1, result.notFound().size());
    }

    @Test
    void shouldBatchLookupWithMeldCardResolution() {
        var meldCard = mock(ScryfallCard.class);
        when(meldCard.id()).thenReturn("meld-id");
        when(meldCard.name()).thenReturn("Meld Card");
        when(meldCard.layout()).thenReturn("meld");
        when(meldCard.set()).thenReturn("set");
        when(meldCard.setName()).thenReturn("Set");
        when(meldCard.typeLine()).thenReturn("Creature");
        when(meldCard.cmc()).thenReturn(5.0);
        when(meldCard.manaCost()).thenReturn("{3}{R}{W}");
        when(meldCard.imageUris()).thenReturn(new ScryfallImageUris("s.jpg", "n.jpg", "l.jpg", "p.png", "a.png", "b.png"));
        var related = new ScryfallRelatedCard("result-id", "meld_result", "Result", "", "");
        when(meldCard.allParts()).thenReturn(List.of(related));

        var meldResultCard = mock(ScryfallCard.class);
        when(meldResultCard.id()).thenReturn("result-id");
        when(meldResultCard.imageUris()).thenReturn(new ScryfallImageUris("", "https://img.scryfall.com/meld-result.png", "", "", "", ""));

        var collectionResponse = new ScryfallCollectionResponse(List.of(meldCard), List.of());
        var meldResponse = new ScryfallCollectionResponse(List.of(meldResultCard), List.of());

        when(scryfallService.lookupByCollection(any()))
                .thenReturn(Uni.createFrom().item(collectionResponse))
                .thenReturn(Uni.createFrom().item(meldResponse));

        BatchResult result = cardService.batchLookupByNames(List.of("Meld Card")).await().indefinitely();

        assertEquals(1, result.cards().size());
    }

    @Test
    void shouldStopWhenPageExceedsMaxPages() {
        cardService = new ScryfallCardService(scryfallService, 0);
        List<ScryfallCard> result = cardService.searchAllCards("Bolt").await().indefinitely();
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotPaginateWhenHasMoreButNoNextPage() {
        var response = new ScryfallListResponse("list", List.of(mock(ScryfallCard.class)), true, null, null, null);

        when(scryfallService.searchCards(anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Uni.createFrom().item(response));

        List<ScryfallCard> result = cardService.searchAllCards("Bolt").await().indefinitely();

        assertEquals(1, result.size());
    }

    @Test
    void shouldTreatMeldCardWithNullPartsAsNonMeld() {
        var scryfallCard = mock(ScryfallCard.class);
        when(scryfallCard.id()).thenReturn("id");
        when(scryfallCard.name()).thenReturn("Meld Card");
        when(scryfallCard.layout()).thenReturn("meld");
        when(scryfallCard.allParts()).thenReturn(null);
        when(scryfallCard.set()).thenReturn("set");
        when(scryfallCard.setName()).thenReturn("Set");
        when(scryfallCard.typeLine()).thenReturn("Creature");
        when(scryfallCard.cmc()).thenReturn(5.0);
        when(scryfallCard.manaCost()).thenReturn("{3}{R}{W}");
        when(scryfallCard.imageUris()).thenReturn(new ScryfallImageUris("s.jpg", "n.jpg", "l.jpg", "p.png", "a.png", "b.png"));

        var result = cardService.toCubeCobraCard(scryfallCard);
        assertEquals("Meld Card", result.await().indefinitely().getName());
    }

    @Test
    void shouldHandleNullNotFoundInCollectionResponse() {
        var card = mock(ScryfallCard.class);
        when(card.id()).thenReturn("id");
        when(card.name()).thenReturn("Test Card");
        when(card.layout()).thenReturn("normal");
        when(card.set()).thenReturn("set");
        when(card.setName()).thenReturn("Set");
        when(card.typeLine()).thenReturn("Instant");
        when(card.cmc()).thenReturn(1.0);
        when(card.manaCost()).thenReturn("{R}");
        when(card.imageUris()).thenReturn(new ScryfallImageUris("s.jpg", "n.jpg", "l.jpg", "p.png", "a.png", "b.png"));

        var response = new ScryfallCollectionResponse(List.of(card), null);

        when(scryfallService.lookupByCollection(any())).thenReturn(Uni.createFrom().item(response));

        BatchResult result = cardService.batchLookupByNames(List.of("Test Card")).await().indefinitely();

        assertEquals(1, result.cards().size());
    }

    @Test
    void shouldHandleNullDataInMeldResponse() {
        var meldCard = mock(ScryfallCard.class);
        when(meldCard.id()).thenReturn("meld-id");
        when(meldCard.name()).thenReturn("Meld Card");
        when(meldCard.layout()).thenReturn("meld");
        when(meldCard.set()).thenReturn("set");
        when(meldCard.setName()).thenReturn("Set");
        when(meldCard.typeLine()).thenReturn("Creature");
        when(meldCard.cmc()).thenReturn(5.0);
        when(meldCard.manaCost()).thenReturn("{3}{R}{W}");
        when(meldCard.imageUris()).thenReturn(new ScryfallImageUris("s.jpg", "n.jpg", "l.jpg", "p.png", "a.png", "b.png"));
        var related = new ScryfallRelatedCard("result-id", "meld_result", "Result", "", "");
        when(meldCard.allParts()).thenReturn(List.of(related));

        var collectionResponse = new ScryfallCollectionResponse(List.of(meldCard), List.of());
        var meldResponse = new ScryfallCollectionResponse(null, null);

        when(scryfallService.lookupByCollection(any()))
                .thenReturn(Uni.createFrom().item(collectionResponse))
                .thenReturn(Uni.createFrom().item(meldResponse));

        BatchResult result = cardService.batchLookupByNames(List.of("Meld Card")).await().indefinitely();

        assertEquals(1, result.cards().size());
    }

    @Test
    void shouldHandleMeldCardWhenResultIdNotInMeldImages() {
        var meldCard = mock(ScryfallCard.class);
        when(meldCard.id()).thenReturn("meld-id");
        when(meldCard.name()).thenReturn("Meld Card");
        when(meldCard.layout()).thenReturn("meld");
        when(meldCard.set()).thenReturn("set");
        when(meldCard.setName()).thenReturn("Set");
        when(meldCard.typeLine()).thenReturn("Creature");
        when(meldCard.cmc()).thenReturn(5.0);
        when(meldCard.manaCost()).thenReturn("{3}{R}{W}");
        when(meldCard.imageUris()).thenReturn(new ScryfallImageUris("s.jpg", "n.jpg", "l.jpg", "p.png", "a.png", "b.png"));
        var related = new ScryfallRelatedCard("result-id", "meld_result", "Result", "", "");
        when(meldCard.allParts()).thenReturn(List.of(related));

        var meldResultCard = mock(ScryfallCard.class);
        when(meldResultCard.id()).thenReturn("different-id");
        when(meldResultCard.imageUris()).thenReturn(new ScryfallImageUris("", "https://img.scryfall.com/meld-result.png", "", "", "", ""));

        var collectionResponse = new ScryfallCollectionResponse(List.of(meldCard), List.of());
        var meldResponse = new ScryfallCollectionResponse(List.of(meldResultCard), List.of());

        when(scryfallService.lookupByCollection(any()))
                .thenReturn(Uni.createFrom().item(collectionResponse))
                .thenReturn(Uni.createFrom().item(meldResponse));

        BatchResult result = cardService.batchLookupByNames(List.of("Meld Card")).await().indefinitely();

        assertEquals(1, result.cards().size());
    }

    @Test
    void shouldFallbackWhenMeldResultFetchFails() {
        var scryfallCard = mock(ScryfallCard.class);
        when(scryfallCard.id()).thenReturn("id");
        when(scryfallCard.name()).thenReturn("Meld Card");
        when(scryfallCard.layout()).thenReturn("meld");
        when(scryfallCard.allParts()).thenReturn(List.of(new ScryfallRelatedCard("result-id", "meld_result", "Result", "", "")));
        when(scryfallCard.set()).thenReturn("set");
        when(scryfallCard.setName()).thenReturn("Set");
        when(scryfallCard.typeLine()).thenReturn("Creature");
        when(scryfallCard.cmc()).thenReturn(5.0);
        when(scryfallCard.manaCost()).thenReturn("{3}{R}{W}");
        when(scryfallCard.imageUris()).thenReturn(new ScryfallImageUris("s.jpg", "n.jpg", "l.jpg", "p.png", "a.png", "b.png"));

        when(scryfallService.getCardById("result-id")).thenReturn(Uni.createFrom().failure(new RuntimeException("API error")));

        var result = cardService.toCubeCobraCard(scryfallCard);
        assertNotNull(result.await().indefinitely());
        assertEquals("Meld Card", result.await().indefinitely().getName());
    }
}
