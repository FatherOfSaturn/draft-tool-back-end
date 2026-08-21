package org.magic.accountService.app;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.magic.accountService.api.Account;
import org.magic.accountService.api.CreateDeckRequest;
import org.magic.accountService.api.Deck;
import org.magic.accountService.api.LoginResponse;
import org.magic.accountService.api.UpdateDeckRequest;
import org.magic.accountService.api.UpdateDisplayNameRequest;
import org.magic.accountService.external.GoogleTokenVerifier;
import org.magic.accountService.external.GoogleTokenVerifier.GoogleUser;
import org.magic.common.admin.AdminService;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service layer for user accounts. Handles login via Google OAuth token verification,
 * account creation/retrieval, profile updates, and deck lifecycle operations.
 * All operations are keyed by the authenticated Google {@code sub} (never by
 * client-supplied account IDs) and verify deck ownership before mutation.
 */
@ApplicationScoped
public class AccountWorker {

    private final GoogleTokenVerifier tokenVerifier;
    private final AccountDbHandler accountDbHandler;
    private final DeckDbHandler deckDbHandler;
    private final JwtService jwtService;
    private final AdminService adminService;

    @Inject
    public AccountWorker(final GoogleTokenVerifier tokenVerifier,
                          final AccountDbHandler accountDbHandler,
                          final DeckDbHandler deckDbHandler,
                          final JwtService jwtService,
                          final AdminService adminService) {
        this.tokenVerifier = tokenVerifier;
        this.accountDbHandler = accountDbHandler;
        this.deckDbHandler = deckDbHandler;
        this.jwtService = jwtService;
        this.adminService = adminService;
    }

    /**
     * Authenticates a user via Google ID token. If the user already exists (matched by
     * Google sub), returns the existing account. Otherwise, creates a new account.
     * On success, mints a signed app JWT for the user.
     *
     * @param idToken the Google OAuth ID token
     * @return the {@link LoginResponse} with the account and JWT, or {@code null} if verification fails
     */
    public Uni<LoginResponse> login(final String idToken) {
        return Uni.createFrom().item(() -> {
            GoogleUser googleUser = tokenVerifier.verify(idToken);
            if (googleUser == null) {
                return null;
            }

            Optional<Account> existing = accountDbHandler.findByGoogleSub(googleUser.sub());
            Account account = existing.orElseGet(() ->
                    accountDbHandler.createAccount(googleUser.sub(), googleUser.email(), googleUser.name()));

            Set<String> groups = new HashSet<>(List.of("user"));
            if (adminService.isAdmin(account.getEmail())) {
                groups.add("admin");
            }

            String jwt = jwtService.issueToken(account.getGoogleSub(), account.getEmail(), groups);
            return new LoginResponse(account, jwt, jwtService.getTokenTtlSeconds());
        });
    }

    public Uni<Optional<Account>> getAccount(final String googleSub) {
        return Uni.createFrom().item(() -> accountDbHandler.findByGoogleSub(googleSub));
    }

    public Uni<Boolean> updateDisplayName(final String googleSub, final UpdateDisplayNameRequest request) {
        return Uni.createFrom().item(() -> {
            Optional<Account> account = accountDbHandler.findByGoogleSub(googleSub);
            if (account.isEmpty()) return false;

            accountDbHandler.updateDisplayName(account.get().getAccountID(), request.displayName());
            return true;
        });
    }

    public Uni<List<Deck>> getDecks(final String googleSub) {
        return Uni.createFrom().item(() -> resolveAccountID(googleSub)
                .map(deckDbHandler::findByAccountID)
                .orElseGet(List::of));
    }

    /**
     * Creates a new deck for the authenticated user. The account must already exist.
     * The deck ID is also added to the account's deck list.
     *
     * @param googleSub the authenticated user's Google subject
     * @param request   the deck creation payload
     * @return an Optional containing the new deck ID, or empty if the account wasn't found
     */
    public Uni<Optional<String>> createDeck(final String googleSub, final CreateDeckRequest request) {
        return Uni.createFrom().item(() -> {
            Optional<String> accountID = resolveAccountID(googleSub);
            if (accountID.isEmpty()) return Optional.empty();

            String deckID = deckDbHandler.createDeck(
                    accountID.get(),
                    request.name(),
                    request.description(),
                    request.cardIds());

            accountDbHandler.addDeckToAccount(accountID.get(), deckID);
            return Optional.of(deckID);
        });
    }

    /**
     * Retrieves a deck belonging to the authenticated user. Decks owned by other
     * accounts are treated as not found.
     */
    public Uni<Optional<Deck>> getDeck(final String googleSub, final String deckID) {
        return Uni.createFrom().item(() -> findOwnedDeck(googleSub, deckID));
    }

    /**
     * Updates a deck belonging to the authenticated user. Decks owned by other
     * accounts are treated as not found.
     */
    public Uni<Boolean> updateDeck(final String googleSub, final String deckID, final UpdateDeckRequest request) {
        return Uni.createFrom().item(() -> {
            Optional<Deck> deck = findOwnedDeck(googleSub, deckID);
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
     * Deletes a deck belonging to the authenticated user and removes its ID from
     * the owning account's deck list. Decks owned by other accounts are treated
     * as not found.
     *
     * @param googleSub the authenticated user's Google subject
     * @param deckID    the deck to delete
     * @return {@code true} if the deck was found and deleted, {@code false} otherwise
     */
    public Uni<Boolean> deleteDeck(final String googleSub, final String deckID) {
        return Uni.createFrom().item(() -> {
            Optional<Deck> deck = findOwnedDeck(googleSub, deckID);
            if (deck.isEmpty()) return false;

            deckDbHandler.deleteDeck(deckID);
            accountDbHandler.removeDeckFromAccount(deck.get().getAccountID(), deckID);
            return true;
        });
    }

    private Optional<String> resolveAccountID(final String googleSub) {
        return accountDbHandler.findByGoogleSub(googleSub)
                .map(Account::getAccountID);
    }

    private Optional<Deck> findOwnedDeck(final String googleSub, final String deckID) {
        Optional<String> accountID = resolveAccountID(googleSub);
        if (accountID.isEmpty()) return Optional.empty();

        Optional<Deck> deck = deckDbHandler.findById(deckID);
        if (deck.isEmpty() || !accountID.get().equals(deck.get().getAccountID())) {
            return Optional.empty();
        }
        return deck;
    }
}
