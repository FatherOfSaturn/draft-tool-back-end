package org.magic.draft.api.scryfall;

import java.util.List;

import org.magic.draft.api.scryfall.cardSearchParts.ScryfallCard;

public class ScryfallCardSearchResponse {
    private String object;
    private Long totalCards;
    private Boolean hasMore;
    private String nextPage;
    private List<ScryfallCard> data;

    public String getObject() { return object; }
    public void setObject(String value) { this.object = value; }

    public Long getTotalCards() { return totalCards; }
    public void setTotalCards(Long value) { this.totalCards = value; }

    public Boolean getHasMore() { return hasMore; }
    public void setHasMore(Boolean value) { this.hasMore = value; }

    public String getNextPage() { return nextPage; }
    public void setNextPage(String value) { this.nextPage = value; }

    public List<ScryfallCard> getData() { return data; }
    public void setData(List<ScryfallCard> value) { this.data = value; }
}
