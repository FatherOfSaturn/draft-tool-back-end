package org.magic.accountService.app;

import java.time.LocalDateTime;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.magic.accountService.api.Deck;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive repository for decks. Queries are by owning account or ID.
 */
@ApplicationScoped
public class DeckDbHandler implements ReactivePanacheMongoRepositoryBase<Deck, String> {

    public Uni<List<Deck>> findByAccountID(final String accountID) {
        return list("accountID = ?1", accountID);
    }

    public Uni<String> createDeck(final String accountID, final String name,
                                  final String description, final List<String> cardIds) {
        var deck = new Deck();
        deck.setDeckID(new ObjectId().toHexString());
        deck.setAccountID(accountID);
        deck.setName(name);
        deck.setDescription(description);
        deck.setCardIds(cardIds);
        deck.setCreatedAt(LocalDateTime.now());
        deck.setUpdatedAt(LocalDateTime.now());
        return persist(deck).replaceWith(deck.getDeckID());
    }

    public Uni<Void> updateDeck(final String deckID, final String name,
                                final String description, final List<String> cardIds) {
        return mongoCollection()
                .updateOne(new Document("_id", deckID),
                        new Document("$set", new Document("name", name)
                                .append("description", description)
                                .append("cardIds", cardIds)
                                .append("updatedAt", LocalDateTime.now())))
                .replaceWithVoid();
    }

    public Uni<Void> deleteDeck(final String deckID) {
        return deleteById(deckID).replaceWithVoid();
    }
}
