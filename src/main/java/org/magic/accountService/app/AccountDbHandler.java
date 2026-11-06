package org.magic.accountService.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.bson.types.ObjectId;
import com.mongodb.client.MongoCollection;
import org.magic.accountService.api.Account;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * MongoDB data access handler for user accounts. Provides operations for
 * account lookup, creation, display name updates, and deck list management.
 */
@ApplicationScoped
public class AccountDbHandler implements PanacheMongoRepository<Account> {

    MongoCollection<Account> getCollection() {
        return mongoCollection();
    }



    public Optional<Account> findByGoogleSub(final String googleSub) {
        var collection = getCollection();
        var doc = collection.find(new org.bson.Document("googleSub", googleSub)).first();
        return Optional.ofNullable(doc);
    }

    public Optional<Account> findById(final String accountID) {
        var collection = getCollection();
        var doc = collection.find(new org.bson.Document("_id", accountID)).first();
        return Optional.ofNullable(doc);
    }

    public Account createAccount(final String googleSub, final String email, final String displayName) {
        var account = new Account();
        account.setAccountID(new ObjectId().toHexString());
        account.setGoogleSub(googleSub);
        account.setEmail(email);
        account.setDisplayName(displayName);
        account.setDeckIDs(new ArrayList<>());
        account.setCreatedAt(LocalDateTime.now());

        var collection = getCollection();
        collection.insertOne(account);
        return account;
    }

    public void updateDisplayName(final String accountID, final String displayName) {
        var collection = getCollection();
        collection.updateOne(
                new org.bson.Document("_id", accountID),
                new org.bson.Document("$set", new org.bson.Document("displayName", displayName)));
    }

    public void addDeckToAccount(final String accountID, final String deckID) {
        var collection = getCollection();
        collection.updateOne(
                new org.bson.Document("_id", accountID),
                new org.bson.Document("$push", new org.bson.Document("deckIDs", deckID)));
    }

    public void removeDeckFromAccount(final String accountID, final String deckID) {
        var collection = getCollection();
        collection.updateOne(
                new org.bson.Document("_id", accountID),
                new org.bson.Document("$pull", new org.bson.Document("deckIDs", deckID)));
    }
}
