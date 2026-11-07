package org.magic.accountService.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.magic.accountService.api.Account;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive MongoDB data access handler for user accounts. Provides operations
 * for account lookup, creation, display name updates, and deck list management.
 */
@ApplicationScoped
public class AccountDbHandler implements ReactivePanacheMongoRepository<Account> {

    public Uni<Optional<Account>> findByGoogleSub(final String googleSub) {
        return mongoCollection().find(new Document("googleSub", googleSub)).collect().first()
                .map(Optional::ofNullable);
    }

    public Uni<Optional<Account>> findById(final String accountID) {
        return mongoCollection().find(new Document("_id", accountID)).collect().first()
                .map(Optional::ofNullable);
    }

    public Uni<Account> createAccount(final String googleSub, final String email, final String displayName) {
        var account = new Account();
        account.setAccountID(new ObjectId().toHexString());
        account.setGoogleSub(googleSub);
        account.setEmail(email);
        account.setDisplayName(displayName);
        account.setDeckIDs(new ArrayList<>());
        account.setCreatedAt(LocalDateTime.now());

        return mongoCollection().insertOne(account).map(ignored -> account);
    }

    public Uni<Void> updateDisplayName(final String accountID, final String displayName) {
        return mongoCollection().updateOne(
                        new Document("_id", accountID),
                        new Document("$set", new Document("displayName", displayName)))
                .replaceWithVoid();
    }

    public Uni<Void> addDeckToAccount(final String accountID, final String deckID) {
        return mongoCollection().updateOne(
                        new Document("_id", accountID),
                        new Document("$push", new Document("deckIDs", deckID)))
                .replaceWithVoid();
    }

    public Uni<Void> removeDeckFromAccount(final String accountID, final String deckID) {
        return mongoCollection().updateOne(
                        new Document("_id", accountID),
                        new Document("$pull", new Document("deckIDs", deckID)))
                .replaceWithVoid();
    }
}
