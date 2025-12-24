package org.magic.draft.external;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.magic.draft.api.scryfall.ScryfallCardSearchResponse;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

//https://api.scryfall.com
@RegisterRestClient(configKey = "scryfallUrl")
public interface ScryfallService {

    /*
     * If you provide the fuzzy parameter and a card name matches that string, then that card is returned. 
     * If not, a fuzzy search is executed for your card name. The server allows misspellings and partial 
     * words to be provided. For example: jac bele will match Jace Beleren.
     * When fuzzy searching, a card is returned if the server is confident that you unambiguously 
     * identified a unique name with your string. Otherwise, you will receive a 404 Error object describing 
     * the problem: either more than 1 one card matched your search, or zero cards matched.
     * You may also provide a set code in the set parameter, in which case the name search and the 
     * returned card print will be limited to the specified set. For both exact and fuzzy, 
     * card names are case-insensitive and punctuation is optional (you can drop apostrophes and periods etc). 
     * 
     * For example: fIReBALL is the same as Fireball and smugglers copter is the same as Smuggler's Copter.
     */
    @GET
    @Path("/cards/named")
    public Uni<JsonObject> getCardFuzzyName(@QueryParam("fuzzy") String fuzzyCardName);

    @GET
    @Path("/cards/{id}")
    public Uni<JsonObject> getCardByID(@PathParam("id") UUID cardID);

    @GET
    @Path("/cards/search")
    public Uni<ScryfallCardSearchResponse> getCardsInBoosterForSet(@QueryParam("q") String set);
    
    // Helper method
    default Uni<ScryfallCardSearchResponse> getBoosterForSet(String setCode) {
        String query = "s:" + setCode + " is:booster";

        // may get double encoded
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        return getCardsInBoosterForSet(encodedQuery);
    }

}