package org.magic.accountService.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.magic.accountService.api.Deck;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive MongoDB data access handler for decks. Provides CRUD operations for
 * deck documents tied to user accounts.
 */
@ApplicationScoped
public class DeckDbHandler implements ReactivePanacheMongoRepository<Deck> {

    public Uni<List<Deck>> findByAccountID(final String accountID) {
        return mongoCollection().find(new Document("accountID", accountID)).collect().asList();
    }

    public Uni<Optional<Deck>> findById(final String deckID) {
        return mongoCollection().find(new Document("_id", deckID)).collect().first()
                .map(Optional::ofNullable);
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

        return mongoCollection().insertOne(deck).map(ignored -> deck.getDeckID());
    }

    public Uni<Void> updateDeck(final String deckID, final String name,
                                final String description, final List<String> cardIds) {
        return mongoCollection().updateOne(
                        new Document("_id", deckID),
                        new Document("$set", new Document("name", name)
                                .append("description", description)
                                .append("cardIds", cardIds)
                                .append("updatedAt", LocalDateTime.now())))
                .replaceWithVoid();
    }

    public Uni<Void> deleteDeck(final String deckID) {
        return mongoCollection().deleteOne(new Document("_id", deckID)).replaceWithVoid();
    }
}
