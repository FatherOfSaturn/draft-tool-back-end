package org.magic.accountService;

import java.net.URI;

import org.magic.accountService.api.Account;
import org.magic.accountService.api.CreateDeckRequest;
import org.magic.accountService.api.Deck;
import org.magic.accountService.api.LoginRequest;
import org.magic.accountService.api.LoginResponse;
import org.magic.accountService.api.UpdateDeckRequest;
import org.magic.accountService.api.UpdateDisplayNameRequest;
import org.magic.accountService.app.AccountWorker;
import org.magic.common.TokenRevocationService;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST resource for user accounts and deck management. Provides endpoints
 * for Google OAuth login, profile updates, and CRUD operations on decks.
 * All endpoints except login require a valid app JWT; the caller's identity
 * is taken from the token, never from the request path.
 */
@Path("/account")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@Tag(name = "Account", description = "User accounts, authentication, and saved decks.")
public class AccountResource {

    private final AccountWorker accountService;
    private final TokenRevocationService tokenRevocationService;

    public AccountResource(final AccountWorker accountService,
                           final TokenRevocationService tokenRevocationService) {
        this.accountService = accountService;
        this.tokenRevocationService = tokenRevocationService;
    }

    /**
     * Authenticates a user via Google OAuth ID token. If the user already exists,
     * returns the existing account; otherwise creates a new one. On success returns
     * the account plus a signed app JWT.
     *
     * @param request the login payload containing the Google ID token
     * @return the account and JWT, or 401 if verification fails
     */
    @POST
    @Path("/login")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Authenticate with Google",
            description = "Verifies a Google OAuth ID token and returns the matching account plus a signed app JWT. " +
                    "A new account is created on first login. Returns 401 if the token cannot be verified.")
    public Uni<LoginResponse> login(@Valid final LoginRequest request) {
        return accountService.login(request.idToken())
                .map(loginResponse -> {
                    if (loginResponse == null) {
                        throw new WebApplicationException(Response.Status.UNAUTHORIZED);
                    }
                    return loginResponse;
                });
    }

    /**
     * Revokes the caller's bearer token so it can no longer be used. The raw
     * token is never stored; only a hash is retained until its natural expiry.
     *
     * @param authorization the {@code Authorization: Bearer <token>} header
     * @return 204 No Content
     */
    @POST
    @Path("/logout")
    @PermitAll
    @Operation(summary = "Log out",
            description = "Revokes the presented bearer token so it can no longer be used. " +
                    "Returns 400 if no bearer token is supplied.")
    public Response logout(@HeaderParam("Authorization") final String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new WebApplicationException("Missing bearer token", Response.Status.BAD_REQUEST);
        }
        tokenRevocationService.revoke(authorization.substring("Bearer ".length()).trim());
        return Response.noContent().build();
    }

    /**
     * Retrieves the authenticated caller's account.
     *
     * @param securityContext the caller's security context
     * @return the account, or 404 if not found
     */
    @GET
    @Path("/")
    @Operation(summary = "Get own account",
            description = "Returns the authenticated caller's account profile. Identity is taken from the JWT.")
    public Uni<Account> getAccount(@Context final SecurityContext securityContext) {
        return accountService.getAccount(callerSubject(securityContext))
                .map(opt -> opt.orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND)));
    }

    /**
     * Updates the display name for the authenticated caller.
     *
     * @param request   the new display name
     * @param securityContext the caller's security context
     * @return 200 OK if updated, 404 if the account was not found
     */
    @PATCH
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update display name",
            description = "Sets a new display name for the authenticated caller. " +
                    "Names are limited to 50 characters and must not contain control characters.")
    public Uni<Response> updateDisplayName(@Valid final UpdateDisplayNameRequest request,
                                           @Context final SecurityContext securityContext) {
        return accountService.updateDisplayName(callerSubject(securityContext), request)
                .map(updated -> updated ? Response.ok().build()
                                        : Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Lists all decks belonging to the authenticated caller.
     *
     * @param securityContext the caller's security context
     * @return the list of decks
     */
    @GET
    @Path("/decks")
    @Operation(summary = "List own decks",
            description = "Returns all decks belonging to the authenticated caller.")
    public Uni<java.util.List<Deck>> getDecks(@Context final SecurityContext securityContext) {
        return accountService.getDecks(callerSubject(securityContext));
    }

    /**
     * Creates a new deck for the authenticated caller.
     *
     * @param request   the deck creation payload
     * @param securityContext the caller's security context
     * @return 201 Created with the new deck's location, or 404 if the account was not found
     */
    @POST
    @Path("/decks")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a deck",
            description = "Creates a new deck for the authenticated caller. " +
                    "Deck names are limited to 100 characters, descriptions to 2000, " +
                    "and a deck may contain at most 500 cards.")
    public Uni<Response> createDeck(@Valid final CreateDeckRequest request,
                                    @Context final SecurityContext securityContext) {
        return accountService.createDeck(callerSubject(securityContext), request)
                .map(opt -> opt.map(deckID -> Response.created(URI.create("/account/decks/" + deckID)).build())
                               .orElse(Response.status(Response.Status.NOT_FOUND).build()));
    }

    /**
     * Retrieves a specific deck belonging to the authenticated caller.
     *
     * @param deckID    the deck to fetch
     * @param securityContext the caller's security context
     * @return the deck, or 404 if not found
     */
    @GET
    @Path("/decks/{deckID}")
    @Operation(summary = "Get a deck",
            description = "Returns one of the authenticated caller's decks by ID, or 404.")
    public Uni<Deck> getDeck(@PathParam("deckID") @Size(max = 64) final String deckID,
                             @Context final SecurityContext securityContext) {
        return accountService.getDeck(callerSubject(securityContext), deckID)
                .map(opt -> opt.orElseThrow(() -> new WebApplicationException(Response.Status.NOT_FOUND)));
    }

    /**
     * Updates a deck belonging to the authenticated caller.
     *
     * @param deckID    the deck to update
     * @param request   the updated deck data
     * @param securityContext the caller's security context
     * @return 200 OK if updated, 404 if the deck was not found
     */
    @PUT
    @Path("/decks/{deckID}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a deck",
            description = "Replaces the name, description, and card list of one of the authenticated caller's decks.")
    public Uni<Response> updateDeck(@PathParam("deckID") @Size(max = 64) final String deckID,
                                    @Valid final UpdateDeckRequest request,
                                    @Context final SecurityContext securityContext) {
        return accountService.updateDeck(callerSubject(securityContext), deckID, request)
                .map(updated -> updated ? Response.ok().build()
                                        : Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Deletes a deck belonging to the authenticated caller.
     *
     * @param deckID    the deck to delete
     * @param securityContext the caller's security context
     * @return 204 No Content if deleted, 404 if the deck was not found
     */
    @DELETE
    @Path("/decks/{deckID}")
    @Operation(summary = "Delete a deck",
            description = "Permanently deletes one of the authenticated caller's decks, returning 204, or 404.")
    public Uni<Response> deleteDeck(@PathParam("deckID") @Size(max = 64) final String deckID,
                                    @Context final SecurityContext securityContext) {
        return accountService.deleteDeck(callerSubject(securityContext), deckID)
                .map(deleted -> deleted ? Response.noContent().build()
                                        : Response.status(Response.Status.NOT_FOUND).build());
    }

    private static String callerSubject(final SecurityContext securityContext) {
        return securityContext.getUserPrincipal().getName();
    }
}
