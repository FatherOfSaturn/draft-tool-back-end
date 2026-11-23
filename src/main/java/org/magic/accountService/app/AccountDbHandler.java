package org.magic.accountService.app;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.magic.accountService.api.Account;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive repository for user accounts. Lookups are by Google subject or ID;
 * deck-list membership is maintained with atomic {@code $push}/{@code $pull}
 * updates so concurrent deck creation cannot clobber the array.
 */
@ApplicationScoped
public class AccountDbHandler implements ReactivePanacheMongoRepositoryBase<Account, String> {

    public Uni<Optional<Account>> findByGoogleSub(final String googleSub) {
        return find("googleSub = ?1", googleSub).firstResultOptional();
    }

    public Uni<Account> createAccount(final String googleSub, final String email, final String displayName) {
        var account = new Account();
        account.setAccountID(new ObjectId().toHexString());
        account.setGoogleSub(googleSub);
        account.setEmail(email);
        account.setDisplayName(displayName);
        account.setDeckIDs(new ArrayList<>());
        account.setCreatedAt(LocalDateTime.now());
        return persist(account).replaceWith(account);
    }

    public Uni<Void> updateDisplayName(final String accountID, final String displayName) {
        return mongoCollection()
                .updateOne(new Document("_id", accountID),
                        new Document("$set", new Document("displayName", displayName)))
                .replaceWithVoid();
    }

    public Uni<Void> addDeckToAccount(final String accountID, final String deckID) {
        return mongoCollection()
                .updateOne(new Document("_id", accountID),
                        new Document("$push", new Document("deckIDs", deckID)))
                .replaceWithVoid();
    }

    public Uni<Void> removeDeckFromAccount(final String accountID, final String deckID) {
        return mongoCollection()
                .updateOne(new Document("_id", accountID),
                        new Document("$pull", new Document("deckIDs", deckID)))
                .replaceWithVoid();
    }
}
