package org.magic.accountService.app;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.magic.accountService.api.Account;
import org.magic.accountService.api.Deck;
import org.magic.accountService.api.DeckPayload;
import org.magic.accountService.api.LoginResponse;
import org.magic.accountService.api.UpdateDisplayNameRequest;
import org.magic.accountService.external.GoogleTokenVerifier;
import org.magic.accountService.external.GoogleTokenVerifier.GoogleUser;
import org.magic.common.admin.AdminService;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Reactive service layer for user accounts. Handles login via Google OAuth token
 * verification, account creation/retrieval, profile updates, and deck lifecycle
 * operations. All operations are keyed by the authenticated Google {@code sub}
 * (never by client-supplied account IDs) and verify deck ownership before mutation.
 *
 * <p>Google ID-token verification performs a blocking JWKS HTTP fetch plus RSA
 * signature checks, so it runs on the worker executor; every other step is a
 * reactive MongoDB operation.</p>
 */
@ApplicationScoped
public class AccountWorker {

    private final GoogleTokenVerifier tokenVerifier;
    private final AccountDbHandler accountDbHandler;
    private final DeckDbHandler deckDbHandler;
    private final JwtService jwtService;
    private final AdminService adminService;
    private final ManagedExecutor blockingExecutor;

    @Inject
    public AccountWorker(final GoogleTokenVerifier tokenVerifier,
                          final AccountDbHandler accountDbHandler,
                          final DeckDbHandler deckDbHandler,
                          final JwtService jwtService,
                          final AdminService adminService,
                          final ManagedExecutor blockingExecutor) {
        this.tokenVerifier = tokenVerifier;
        this.accountDbHandler = accountDbHandler;
        this.deckDbHandler = deckDbHandler;
        this.jwtService = jwtService;
        this.adminService = adminService;
        this.blockingExecutor = blockingExecutor;
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
        return Uni.createFrom().item(() -> tokenVerifier.verify(idToken))
                .runSubscriptionOn(blockingExecutor)
                .chain(googleUser -> {
                    if (googleUser == null) {
                        return Uni.createFrom().nullItem();
                    }
                    return accountDbHandler.findByGoogleSub(googleUser.sub())
                            .chain(existing -> existing
                                    .map(account -> Uni.createFrom().item(account))
                                    .orElseGet(() -> accountDbHandler.createAccount(
                                            googleUser.sub(), googleUser.email(), googleUser.name())))
                            .map(account -> toLoginResponse(account));
                });
    }

    private LoginResponse toLoginResponse(final Account account) {
        Set<String> groups = new HashSet<>(List.of("user"));
        if (adminService.isAdmin(account.getEmail())) {
            groups.add("admin");
        }
        String jwt = jwtService.issueToken(account.getGoogleSub(), account.getEmail(), groups);
        return new LoginResponse(account, jwt, jwtService.getTokenTtlSeconds());
    }

    public Uni<Optional<Account>> getAccount(final String googleSub) {
        return accountDbHandler.findByGoogleSub(googleSub);
    }

    public Uni<Boolean> updateDisplayName(final String googleSub, final UpdateDisplayNameRequest request) {
        return accountDbHandler.findByGoogleSub(googleSub)
                .chain(account -> account
                        .map(found -> accountDbHandler.updateDisplayName(found.getAccountID(), request.displayName())
                                .replaceWith(true))
                        .orElseGet(() -> Uni.createFrom().item(false)));
    }

    public Uni<List<Deck>> getDecks(final String googleSub) {
        return resolveAccountID(googleSub)
                .chain(accountID -> accountID
                        .map(deckDbHandler::findByAccountID)
                        .orElseGet(() -> Uni.createFrom().item(List.of())));
    }

    /**
     * Creates a new deck for the authenticated user. The account must already exist.
     * The deck ID is also added to the account's deck list.
     *
     * @param googleSub the authenticated user's Google subject
     * @param request   the deck creation payload
     * @return an Optional containing the new deck ID, or empty if the account wasn't found
     */
    public Uni<Optional<String>> createDeck(final String googleSub, final DeckPayload request) {
        return resolveAccountID(googleSub)
                .chain(accountID -> accountID
                        .map(id -> deckDbHandler.createDeck(id, request.name(), request.description(), request.cardIds())
                                .chain(deckID -> accountDbHandler.addDeckToAccount(id, deckID)
                                        .replaceWith(Optional.of(deckID))))
                        .orElseGet(() -> Uni.createFrom().item(Optional.empty())));
    }

    /**
     * Retrieves a deck belonging to the authenticated user. Decks owned by other
     * accounts are treated as not found.
     */
    public Uni<Optional<Deck>> getDeck(final String googleSub, final String deckID) {
        return findOwnedDeck(googleSub, deckID);
    }

    /**
     * Updates a deck belonging to the authenticated user. Decks owned by other
     * accounts are treated as not found.
     */
    public Uni<Boolean> updateDeck(final String googleSub, final String deckID, final DeckPayload request) {
        return findOwnedDeck(googleSub, deckID)
                .chain(deck -> deck
                        .map(found -> deckDbHandler.updateDeck(
                                        deckID, request.name(), request.description(), request.cardIds())
                                .replaceWith(true))
                        .orElseGet(() -> Uni.createFrom().item(false)));
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
        return findOwnedDeck(googleSub, deckID)
                .chain(deck -> deck
                        .map(found -> deckDbHandler.deleteDeck(deckID)
                                .chain(ignored -> accountDbHandler.removeDeckFromAccount(
                                        found.getAccountID(), deckID))
                                .replaceWith(true))
                        .orElseGet(() -> Uni.createFrom().item(false)));
    }

    private Uni<Optional<String>> resolveAccountID(final String googleSub) {
        return accountDbHandler.findByGoogleSub(googleSub)
                .map(account -> account.map(Account::getAccountID));
    }

    private Uni<Optional<Deck>> findOwnedDeck(final String googleSub, final String deckID) {
        return resolveAccountID(googleSub)
                .chain(accountID -> {
                    if (accountID.isEmpty()) {
                        return Uni.createFrom().item(Optional.empty());
                    }
                    return deckDbHandler.findById(deckID)
                            .map(deck -> deck.filter(found -> accountID.get().equals(found.getAccountID())));
                });
    }
}
