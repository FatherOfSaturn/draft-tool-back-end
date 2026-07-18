package org.magic.accountService;

import java.net.URI;

import org.magic.accountService.api.Account;
import org.magic.accountService.api.CreateDeckRequest;
import org.magic.accountService.api.Deck;
import org.magic.accountService.api.LoginRequest;
import org.magic.accountService.api.UpdateDeckRequest;
import org.magic.accountService.api.UpdateDisplayNameRequest;
import org.magic.accountService.app.AccountWorker;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST resource for user accounts and deck management. Provides endpoints
 * for Google OAuth login, profile updates, and CRUD operations on decks.
 */
@Path("/account")
@Produces(MediaType.APPLICATION_JSON)
public class AccountResource {

    private final AccountWorker accountService;

    public AccountResource(final AccountWorker accountService) {
        this.accountService = accountService;
    }

    /**
     * Authenticates a user via Google OAuth ID token. If the user already exists,
     * returns the existing account; otherwise creates a new one.
     *
     * @param request the login payload containing the Google ID token
     * @return the account, or 401 if verification fails
     */
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Account> login(final LoginRequest request) {
        return accountService.login(request.idToken())
                .map(account -> {
                    if (account == null) {
                        throw new WebApplicationException(Response.Status.UNAUTHORIZED);
                    }
                    return account;
                });
    }

    /**
     * Retrieves an account by its ID.
     *
     * @param accountID the account to fetch
     * @return the account, or 404 if not found
     */
    @GET
    @Path("/{accountID}")
    public Uni<Account> getAccount(@PathParam("accountID") final String accountID) {
        return accountService.getAccount(accountID)
                .map(opt -> opt.orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND)));
    }

    /**
     * Updates the display name for an account.
     *
     * @param accountID the account to update
     * @param request   the new display name
     * @return 200 OK if updated, 404 if the account was not found
     */
    @PATCH
    @Path("/{accountID}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> updateDisplayName(@PathParam("accountID") final String accountID,
                                           final UpdateDisplayNameRequest request) {
        return accountService.updateDisplayName(accountID, request)
                .map(updated -> updated ? Response.ok().build()
                                        : Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Lists all decks belonging to an account.
     *
     * @param accountID the account whose decks to list
     * @return the list of decks
     */
    @GET
    @Path("/{accountID}/decks")
    public Uni<java.util.List<Deck>> getDecks(@PathParam("accountID") final String accountID) {
        return accountService.getDecks(accountID);
    }

    /**
     * Creates a new deck for an account.
     *
     * @param accountID the owner account
     * @param request   the deck creation payload
     * @return 201 Created with the new deck's location, or 404 if the account was not found
     */
    @POST
    @Path("/{accountID}/decks")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> createDeck(@PathParam("accountID") final String accountID,
                                    final CreateDeckRequest request) {
        return accountService.createDeck(accountID, request)
                .map(opt -> opt.map(deckID -> Response.created(URI.create("/account/" + accountID + "/decks/" + deckID)).build())
                               .orElse(Response.status(Response.Status.NOT_FOUND).build()));
    }

    /**
     * Retrieves a specific deck by its ID.
     *
     * @param accountID the owning account (for path consistency)
     * @param deckID    the deck to fetch
     * @return the deck, or 404 if not found
     */
    @GET
    @Path("/{accountID}/decks/{deckID}")
    public Uni<Deck> getDeck(@PathParam("accountID") final String accountID,
                             @PathParam("deckID") final String deckID) {
        return accountService.getDeck(deckID)
                .map(opt -> opt.orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND)));
    }

    /**
     * Updates a deck's name, description, and card list.
     *
     * @param accountID the owning account (for path consistency)
     * @param deckID    the deck to update
     * @param request   the updated deck data
     * @return 200 OK if updated, 404 if the deck was not found
     */
    @PUT
    @Path("/{accountID}/decks/{deckID}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> updateDeck(@PathParam("accountID") final String accountID,
                                    @PathParam("deckID") final String deckID,
                                    final UpdateDeckRequest request) {
        return accountService.updateDeck(deckID, request)
                .map(updated -> updated ? Response.ok().build()
                                        : Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Deletes a deck and removes it from the account's deck list.
     *
     * @param accountID the owning account (for path consistency)
     * @param deckID    the deck to delete
     * @return 204 No Content if deleted, 404 if the deck was not found
     */
    @DELETE
    @Path("/{accountID}/decks/{deckID}")
    public Uni<Response> deleteDeck(@PathParam("accountID") final String accountID,
                                    @PathParam("deckID") final String deckID) {
        return accountService.deleteDeck(deckID)
                .map(deleted -> deleted ? Response.noContent().build()
                                        : Response.status(Response.Status.NOT_FOUND).build());
    }
}
