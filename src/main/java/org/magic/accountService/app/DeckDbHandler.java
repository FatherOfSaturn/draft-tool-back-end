package org.magic.accountService.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import com.mongodb.client.MongoCollection;
import org.magic.accountService.api.Deck;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * MongoDB data access handler for decks. Provides CRUD operations for
 * deck documents tied to user accounts.
 */
@ApplicationScoped
public class DeckDbHandler implements PanacheMongoRepository<Deck> {

    MongoCollection<Deck> getCollection() {
        return mongoCollection();
    }



    public List<Deck> findByAccountID(final String accountID) {
        var collection = getCollection();
        return collection.find(new org.bson.Document("accountID", accountID)).into(new java.util.ArrayList<>());
    }

    public Optional<Deck> findById(final String deckID) {
        var collection = getCollection();
        var doc = collection.find(new org.bson.Document("_id", deckID)).first();
        return Optional.ofNullable(doc);
    }

    public String createDeck(final String accountID, final String name, final String description, final List<String> cardIds) {
        var deck = new Deck();
        deck.setDeckID(new ObjectId().toHexString());
        deck.setAccountID(accountID);
        deck.setName(name);
        deck.setDescription(description);
        deck.setCardIds(cardIds);
        deck.setCreatedAt(LocalDateTime.now());
        deck.setUpdatedAt(LocalDateTime.now());

        var collection = getCollection();
        collection.insertOne(deck);
        return deck.getDeckID();
    }

    public void updateDeck(final String deckID, final String name, final String description, final List<String> cardIds) {
        var collection = getCollection();
        collection.updateOne(
                new org.bson.Document("_id", deckID),
                new org.bson.Document("$set", new org.bson.Document("name", name)
                        .append("description", description)
                        .append("cardIds", cardIds)
                        .append("updatedAt", LocalDateTime.now())));
    }

    public void deleteDeck(final String deckID) {
        var collection = getCollection();
        collection.deleteOne(new org.bson.Document("_id", deckID));
    }
}
