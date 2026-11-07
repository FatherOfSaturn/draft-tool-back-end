package org.magic.burnDraft.app.GameCoordination;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.magic.burnDraft.api.BurnGameInfo;
import org.magic.burnDraft.api.BurnPlayer;
import org.magic.common.game.GameState;
import org.magic.common.mongo.ReactiveMongo;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive repository for burn draft games. Mirrors {@code ClassicDraftDbHandler}:
 * documents are keyed by the business {@code gameID} (the driver-owned {@code _id}
 * is not mapped), mutations use targeted updates so concurrent turns are not
 * lost, and the draft-check projection runs as a Mongo aggregation.
 */
@ApplicationScoped
public class BurnDraftDbHandler implements ReactivePanacheMongoRepository<BurnGameInfo> {
    private static final Logger LOGGER = LogManager.getLogger(BurnDraftDbHandler.class);
    private static final String COLLECTION = "BurnGames";

    public Uni<String> addGame(final BurnGameInfo gameInfo) {
        return mongoCollection().insertOne(gameInfo)
                .map(result -> result.getInsertedId().asObjectId().getValue().toHexString());
    }

    public Uni<BurnGameInfo> findGame(final String gameID) {
        return ReactiveMongo.required(
                find("gameID = ?1", gameID).firstResult(),
                () -> {
                    LOGGER.error("Unable to find Burn Game with ID: {}", gameID);
                    return new jakarta.ws.rs.NotFoundException("Unable to find Burn Game with ID: " + gameID);
                });
    }

    /**
     * Runs the draft-check aggregation: reports the game state and whether the
     * player identified by {@code playerToken} has an active pack with cards.
     */
    public Uni<Document> findDraftCheck(final String gameID, final String playerToken) {
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
                .aggregate(List.of(Aggregates.match(Filters.eq("gameID", gameID)), projectStage))
                .collect().first();
    }

    public Uni<BurnPlayer> updatePlayer(final BurnGameInfo gameInfo, final BurnPlayer player) {
        Bson filter = Filters.and(
                Filters.eq("gameID", gameInfo.getGameID()),
                Filters.eq("players.draftOrderNumber", player.getDraftOrderNumber()));
        return ReactiveMongo.modified(
                mongoCollection().updateOne(filter, Updates.set("players.$", player)),
                () -> player,
                () -> {
                    LOGGER.error("Unable to update Burn Game {}, with Player {}.",
                            gameInfo.getGameID(), player.getPlayerName());
                    return new IllegalStateException("Unable to update Burn Game with player's new info.");
                });
    }

    public Uni<BurnGameInfo> updateGame(final BurnGameInfo gameInfo) {
        Bson update = Updates.combine(
                Updates.set("players", gameInfo.getPlayers()),
                Updates.set("gameState", gameInfo.getGameState()),
                Updates.set("currentPackIndex", gameInfo.getCurrentPackIndex()),
                Updates.set("draftDirection", gameInfo.getDraftDirection()));
        return ReactiveMongo.modified(
                mongoCollection().updateOne(Filters.eq("gameID", gameInfo.getGameID()), update),
                () -> gameInfo,
                () -> {
                    LOGGER.error("Unable to update Burn Game {}.", gameInfo.getGameID());
                    return new IllegalStateException("Unable to update Burn Game with new info.");
                });
    }

    public Uni<GameState> updateGameState(final String gameID, final GameState gameState) {
        return ReactiveMongo.modified(
                mongoCollection().updateOne(Filters.eq("gameID", gameID),
                        Updates.set("gameState", gameState)),
                () -> gameState,
                () -> {
                    LOGGER.error("Unable to update Burn Game {} state.", gameID);
                    return new IllegalStateException("Unable to update Burn Game state.");
                });
    }

    public Uni<Integer> clearGamesWithStatus(final GameState gameState) {
        return delete("gameState = ?1", gameState).map(Long::intValue);
    }

    public Uni<List<BurnGameInfo>> findGamesByPlayerName(final String playerName) {
        return list("players.playerName = ?1", Sort.descending("createdAt"), playerName);
    }

    public Uni<List<BurnGameInfo>> findGamesByAccountID(final String accountID) {
        return list("players.accountID = ?1", Sort.descending("createdAt"), accountID);
    }
}
