package org.magic.accountService.app;

import java.util.List;
import java.util.Optional;

import org.magic.accountService.api.Account;
import org.magic.accountService.api.CreateDeckRequest;
import org.magic.accountService.api.Deck;
import org.magic.accountService.api.UpdateDeckRequest;
import org.magic.accountService.api.UpdateDisplayNameRequest;
import org.magic.accountService.external.GoogleTokenVerifier;
import org.magic.accountService.external.GoogleTokenVerifier.GoogleUser;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service layer for user accounts. Handles login via Google OAuth token verification,
 * account creation/retrieval, profile updates, and deck lifecycle operations.
 * Delegates persistence to {@link AccountDbHandler} and {@link DeckDbHandler}.
 */
@ApplicationScoped
public class AccountWorker {

    private final GoogleTokenVerifier tokenVerifier;
    private final AccountDbHandler accountDbHandler;
    private final DeckDbHandler deckDbHandler;

    @Inject
    public AccountWorker(final GoogleTokenVerifier tokenVerifier,
                          final AccountDbHandler accountDbHandler,
                          final DeckDbHandler deckDbHandler) {
        this.tokenVerifier = tokenVerifier;
        this.accountDbHandler = accountDbHandler;
        this.deckDbHandler = deckDbHandler;
    }

    /**
     * Authenticates a user via Google ID token. If the user already exists (matched by
     * Google sub), returns the existing account. Otherwise, creates a new account.
     *
     * @param idToken the Google OAuth ID token
     * @return the {@link Account}, or {@code null} if verification fails
     */
    public Uni<Account> login(final String idToken) {
        return Uni.createFrom().item(() -> {
            GoogleUser googleUser = tokenVerifier.verify(idToken);
            if (googleUser == null) {
                return null;
            }

            Optional<Account> existing = accountDbHandler.findByGoogleSub(googleUser.sub());
            if (existing.isPresent()) {
                return existing.get();
            }

            return accountDbHandler.createAccount(googleUser.sub(), googleUser.email(), googleUser.name());
        });
    }

    public Uni<Optional<Account>> getAccount(final String accountID) {
        return Uni.createFrom().item(() -> accountDbHandler.findById(accountID));
    }

    public Uni<Boolean> updateDisplayName(final String accountID, final UpdateDisplayNameRequest request) {
        return Uni.createFrom().item(() -> {
            Optional<Account> account = accountDbHandler.findById(accountID);
            if (account.isEmpty()) return false;

            accountDbHandler.updateDisplayName(accountID, request.displayName());
            return true;
        });
    }

    public Uni<List<Deck>> getDecks(final String accountID) {
        return Uni.createFrom().item(() -> deckDbHandler.findByAccountID(accountID));
    }

    /**
     * Creates a new deck for an account. The account must already exist.
     * The deck ID is also added to the account's deck list.
     *
     * @param accountID the owner account ID
     * @param request   the deck creation payload
     * @return an Optional containing the new deck ID, or empty if the account wasn't found
     */
    public Uni<Optional<String>> createDeck(final String accountID, final CreateDeckRequest request) {
        return Uni.createFrom().item(() -> {
            Optional<Account> account = accountDbHandler.findById(accountID);
            if (account.isEmpty()) return Optional.empty();

            String deckID = deckDbHandler.createDeck(
                    accountID,
                    request.name(),
                    request.description(),
                    request.cardIds());

            accountDbHandler.addDeckToAccount(accountID, deckID);
            return Optional.of(deckID);
        });
    }

    public Uni<Optional<Deck>> getDeck(final String deckID) {
        return Uni.createFrom().item(() -> deckDbHandler.findById(deckID));
    }

    public Uni<Boolean> updateDeck(final String deckID, final UpdateDeckRequest request) {
        return Uni.createFrom().item(() -> {
            Optional<Deck> deck = deckDbHandler.findById(deckID);
            if (deck.isEmpty()) return false;

            deckDbHandler.updateDeck(
                    deckID,
                    request.name(),
                    request.description(),
                    request.cardIds());
            return true;
        });
    }

    /**
     * Deletes a deck and removes its ID from the owning account's deck list.
     *
     * @param deckID the deck to delete
     * @return {@code true} if the deck was found and deleted, {@code false} otherwise
     */
    public Uni<Boolean> deleteDeck(final String deckID) {
        return Uni.createFrom().item(() -> {
            Optional<Deck> deck = deckDbHandler.findById(deckID);
            if (deck.isEmpty()) return false;

            deckDbHandler.deleteDeck(deckID);
            accountDbHandler.removeDeckFromAccount(deck.get().getAccountID(), deckID);
            return true;
        });
    }
}
