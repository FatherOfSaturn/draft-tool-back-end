package org.magic.accountService.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.magic.accountService.api.Account;
import org.magic.pyramidDraft.app.GameCoordination.MongoService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * MongoDB data access handler for user accounts. Provides operations for
 * account lookup, creation, display name updates, and deck list management.
 */
@ApplicationScoped
public class AccountDbHandler {

    private static final String COLLECTION_NAME = "Accounts";

    private final MongoService mongoService;

    @Inject
    public AccountDbHandler(final MongoService mongoService) {
        this.mongoService = mongoService;
    }

    public Optional<Account> findByGoogleSub(final String googleSub) {
        var collection = mongoService.getDatabase().getCollection(COLLECTION_NAME, Account.class);
        var doc = collection.find(new org.bson.Document("googleSub", googleSub)).first();
        return Optional.ofNullable(doc);
    }

    public Optional<Account> findById(final String accountID) {
        var collection = mongoService.getDatabase().getCollection(COLLECTION_NAME, Account.class);
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

        var collection = mongoService.getDatabase().getCollection(COLLECTION_NAME, Account.class);
        collection.insertOne(account);
        return account;
    }

    public void updateDisplayName(final String accountID, final String displayName) {
        var collection = mongoService.getDatabase().getCollection(COLLECTION_NAME, Account.class);
        collection.updateOne(
                new org.bson.Document("_id", accountID),
                new org.bson.Document("$set", new org.bson.Document("displayName", displayName)));
    }

    public void addDeckToAccount(final String accountID, final String deckID) {
        var collection = mongoService.getDatabase().getCollection(COLLECTION_NAME, Account.class);
        collection.updateOne(
                new org.bson.Document("_id", accountID),
                new org.bson.Document("$push", new org.bson.Document("deckIDs", deckID)));
    }

    public void removeDeckFromAccount(final String accountID, final String deckID) {
        var collection = mongoService.getDatabase().getCollection(COLLECTION_NAME, Account.class);
        collection.updateOne(
                new org.bson.Document("_id", accountID),
                new org.bson.Document("$pull", new org.bson.Document("deckIDs", deckID)));
    }
}
