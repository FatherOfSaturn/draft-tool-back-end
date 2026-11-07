package org.magic.classicDraft.app.GameCoordination;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.magic.classicDraft.api.ClassicGameInfo;
import org.magic.classicDraft.api.ClassicPlayer;
import org.magic.common.game.GameState;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import io.quarkus.mongodb.FindOptions;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive MongoDB data access handler for classic draft games. Document-level
 * operations (including the draft-check aggregation) use the reactive driver.
 */
@ApplicationScoped
public class ClassicDraftDbHandler implements ReactivePanacheMongoRepository<ClassicGameInfo> {
    private static final Logger LOGGER = LogManager.getLogger(ClassicDraftDbHandler.class);
    private static final String COLLECTION = "ClassicGames";

    public Uni<String> addGame(final ClassicGameInfo gameInfo) {
        return mongoCollection().insertOne(gameInfo)
                .map(result -> result.getInsertedId().asObjectId().getValue().toHexString());
    }

    public Uni<ClassicGameInfo> findGame(final String gameID) {
        Bson filter = Filters.eq("gameID", gameID);
        return mongoCollection().find(filter).collect().first()
                .onItem().ifNull().failWith(() -> {
                    LOGGER.error("Unable to find Classic Game with ID: {}", gameID);
                    return new jakarta.ws.rs.NotFoundException("Unable to find Classic Game with ID: " + gameID);
                })
                .invoke(gameDocument -> LOGGER.info("Found Classic Game Info for game: {}", gameDocument.getGameID()));
    }

    /**
     * Runs the draft-check aggregation: reports the game state and whether the
     * player identified by {@code playerToken} has an active pack with cards.
     * Uses the raw document collection because the projection is computed.
     *
     * @param gameID      the game to check
     * @param playerToken the player's draft token
     * @return a {@link Uni} emitting the aggregation document, or {@code null} if no game matches
     */
    public Uni<Document> findDraftCheck(final String gameID, final String playerToken) {
        Bson filter = Filters.eq("gameID", gameID);

        Document playerVar = new Document("player", new Document(
                "$arrayElemAt", List.of(
                        new Document("$filter", new Document()
                                .append("input", "$players")
                                .append("as", "p")
                                .append("cond", new Document("$eq", List.of("$$p.playerToken", playerToken)))
                        ), 0
                )
        ));

        Document inExpr = new Document("$ifNull", List.of("$$player.activeCardPacks", List.of()));

        Bson projectStage = Aggregates.project(Projections.fields(
                Projections.excludeId(),
                Projections.include("gameState"),
                Projections.computed("canDraft", new Document("$gt", List.of(
                        new Document("$size", new Document("$let", new Document()
                                .append("vars", playerVar)
                                .append("in", inExpr)
                        )),
                        0
                )))
        ));

        return mongoDatabase().getCollection(COLLECTION, Document.class)
                .aggregate(List.of(Aggregates.match(filter), projectStage))
                .collect().first();
    }

    public Uni<ClassicPlayer> updatePlayer(final ClassicGameInfo gameInfo, final ClassicPlayer player) {
        Bson filter = Filters.and(
                Filters.eq("gameID", gameInfo.getGameID()),
                Filters.eq("players.draftOrderNumber", player.getDraftOrderNumber())
        );
        Bson updateOperation = Updates.set("players.$", player);

        return mongoCollection().updateOne(filter, updateOperation)
                .map(result -> {
                    if (result.getModifiedCount() > 0) {
                        LOGGER.info("Successfully updated Player: {} in Game: {}",
                                player.getPlayerName(), gameInfo.getGameID());
                        return player;
                    }
                    LOGGER.error("Unable to update Game {}, with Player {}.",
                            gameInfo.getGameID(), player.getPlayerName());
                    throw new IllegalStateException("Unable to update Classic Game with player's new info.");
                });
    }

    public Uni<ClassicGameInfo> updateGame(final ClassicGameInfo gameInfo) {
        Bson filter = Filters.eq("gameID", gameInfo.getGameID());
        Bson update = Updates.combine(
                Updates.set("players", gameInfo.getPlayers()),
                Updates.set("gameState", gameInfo.getGameState()),
                Updates.set("currentPackIndex", gameInfo.getCurrentPackIndex()),
                Updates.set("draftDirection", gameInfo.getDraftDirection())
        );

        return mongoCollection().updateOne(filter, update)
                .map(result -> {
                    if (result.getModifiedCount() > 0) {
                        LOGGER.info("Successfully updated Classic Game: {}", gameInfo.getGameID());
                        return gameInfo;
                    }
                    LOGGER.error("Unable to update Classic Game {}.", gameInfo.getGameID());
                    throw new IllegalStateException("Unable to update Classic Game with new info.");
                });
    }

    public Uni<GameState> updateGameState(final String gameID, final GameState gameState) {
        Bson filter = Filters.eq("gameID", gameID);
        Bson update = Updates.set("gameState", gameState);

        return mongoCollection().updateOne(filter, update)
                .map(result -> {
                    if (result.getModifiedCount() > 0) {
                        LOGGER.info("Successfully updated Classic Game state: {} -> {}", gameID, gameState);
                        return gameState;
                    }
                    LOGGER.error("Unable to update Classic Game {} state.", gameID);
                    throw new IllegalStateException("Unable to update Classic Game state.");
                });
    }

    public Uni<Integer> clearGamesWithStatus(final GameState gameState) {
        Bson filter = Filters.eq("gameState", gameState);
        return mongoCollection().deleteMany(filter).map(result -> (int) result.getDeletedCount());
    }

    public Uni<List<ClassicGameInfo>> findGamesByPlayerName(final String playerName) {
        Bson filter = Filters.eq("players.playerName", playerName);
        FindOptions options = new FindOptions().sort(Sorts.descending("createdAt"));
        return mongoCollection().find(filter, options).collect().asList();
    }

    public Uni<List<ClassicGameInfo>> findGamesByAccountID(final String accountID) {
        Bson filter = Filters.eq("players.accountID", accountID);
        FindOptions options = new FindOptions().sort(Sorts.descending("createdAt"));
        return mongoCollection().find(filter, options).collect().asList();
    }
}
