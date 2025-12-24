package org.magic.draft.external;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.magic.draft.api.scryfall.ScryfallCardSearchResponse;
import org.magic.draft.api.scryfall.cardSearchParts.ScryfallCard;
import org.magic.draft.util.GenericResolver;
import org.magic.draft.util.GenericRestService;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

//https://api.scryfall.com
@RegisterRestClient(configKey = "scryfallUrl")
public class ScryfallHelper {

    final ScryfallService scryfall;
    final GenericResolver resolver;


    @Inject
    public ScryfallHelper(@RestClient final ScryfallService scryfall,
                                      final GenericResolver resolver) {
        this.scryfall = scryfall;
        this.resolver = resolver;
    }

    public Uni<ScryfallCardSearchResponse> getBoosterForSet(String setCode) {
        String query = "s:" + setCode + " is:booster";

        // may get double encoded
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        return scryfall.getCardsInBoosterForSet(encodedQuery);
                    //    .map(cardList -> {
                    //         if (cardList.getBoolean("has_more")) {
                                
                    //         }
                    //    });
    }

    //wrtie a multi to do this

    private Uni<List<ScryfallCard>> fetchRest(final ScryfallCardSearchResponse firstResponse) {

        if (firstResponse.getHasMore()) {

            Multi.createBy().repeating().uni(
                () -> new AtomicReference<String>(""),
                url -> resolver.invokeUrl(url.get(), ScryfallCardSearchResponse.class)
                            .onItem().invoke(resp -> url.set(resp.getNextPage()))
            ).until(resp -> resp.getHasMore())
            .collect().in(() -> new ArrayList<>(), (list, item) -> list.add(item.getData()));
            // .map(allResponses -> allResponses.stream().)

            // USING DISJOINT REMOVES THE TYPE AND MAKES A GENERIC OBJECT


            // .onItem().disjoint().collect().asList();

        }
        else {
            return Uni.createFrom().item(firstResponse.getData());
        }

    }

    public void blah(final String setCode) throws IOException {
        
        boolean firstCall = true;

        scryfall.getBoosterForSet(setCode)
                .chain(this::fetchRest);



        // can i get the data out of the response object and join all that together
        // Multi.createBy().repeating().uni(
        //     () -> new AtomicReference<String>(null),
        //     state -> {
        //         if (firstCall) {
        //             firstCall = false;
        //             return scryfall.getBoosterForSet(setCode)
        //                            .map(resp -> {
        //                             state.set(resp.getNextPage());
        //                             return resp;
        //                            });
        //         }
        //         else {
        //             return resolver.invokeUrl(state.get(), ScryfallCardSearchResponse.class) 
        //                            .map(resp -> {
        //                             state.set(resp.getNextPage());
        //                             return resp;
        //                            });
        //         }
        //     }
        // ).until(response -> !response.getHasMore())
        // .map(response -> response.getData())
        // .collect().asList();

        // Multi.createBy().repeating().supplier(
        //     () -> new AtomicBoolean(),
        //     state -> scryfall.getCardsInBoosterForSet("mid"))
        //     .until(null)
        //     .whilst(response -> response);
            

        // Multi.createBy().repeating().uni(
        //     () -> new AtomicReference<>(null),
        //     (nextPageUrlHolder) -> {
        //         if (nextPageUrlHolder.get() == null) {
        //             return initialCall().onItem().invoke(apiResponse -> {
        //                 nextPageUrlHolder.set(apiResponse.getNextPage());
        //             });
        //         }
        //         else {
        //             return callApiWithURL(nextPageUrlHolder.get())
        //                           .onItem().invoke(apiResponse -> {
        //                             nextPageUrlHolder.set(apiResponse.getNextPage());
        //                           });
        //         }
        //     }
        // )

        // .whilst(apiResponse -> apiResponse.hasNext() && apiResponse.getNextPage != null)
        // .onItem().disjoint();
    }


    // // Your REST call for a single page
    // Uni<JsonObject> fetchPage(String url) {
    //     // Replace with your REST client call
    //     // e.g., return webClient.get(url).send().map(resp -> resp.bodyAsJsonObject());
    //     return null;
    // }

    // // Recursive method returning a Multi of items
    // Multi<JsonObject> fetchAllPagesStream(String url) {
    //     return Multi.createFrom().deferred(() ->
    //         fetchPage(url)
    //             .flatMapMany(page -> {
    //                 JsonArray data = page.getJsonArray("data");
    //                 boolean hasMore = page.getBoolean("has_more", false);
    //                 String nextPage = page.containsKey("next_page") ? page.getString("next_page") : null;

    //                 // Stream current page items
    //                 Multi<JsonObject> currentPage = Multi.createFrom().items(data.stream()
    //                                                                             .map(v -> (JsonObject)v)
    //                                                                             .toArray(JsonObject[]::new));

    //                 if (hasMore && nextPage != null && !nextPage.isEmpty()) {
    //                     // Concatenate with next pages recursively
    //                     return currentPage.concatWith(fetchAllPagesStream(nextPage));
    //                 } else {
    //                     // Last page
    //                     return currentPage;
    //                 }
    //             })
    //     );
    // }

}