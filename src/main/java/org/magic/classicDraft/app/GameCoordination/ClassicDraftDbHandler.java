package org.magic.classicDraft.app.GameCoordination;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.magic.classicDraft.api.ClassicGameInfo;
import org.magic.classicDraft.api.ClassicGameState;
import org.magic.classicDraft.api.ClassicPlayer;
import org.magic.pyramidDraft.app.GameCoordination.MongoService;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ClassicDraftDbHandler {
    private static final Logger LOGGER = LogManager.getLogger(ClassicDraftDbHandler.class);

    @Inject
    MongoService mongoService;

    public MongoCollection<ClassicGameInfo> getCollection() {
        MongoDatabase database = mongoService.getDatabase();
        return database.getCollection("ClassicGames", ClassicGameInfo.class);
    }

    public MongoCollection<Document> getRawCollection() {
        MongoDatabase database = mongoService.getDatabase();
        return database.getCollection("ClassicGames");
    }

    public String addGame(ClassicGameInfo gameInfo) {
        return getCollection().insertOne(gameInfo).getInsertedId().asObjectId().getValue().toHexString();
    }

    public ClassicGameInfo findGame(String gameID) {
        Bson filter = Filters.eq("gameID", gameID);
        ClassicGameInfo gameDocument = getCollection().find(filter).first();

        if (gameDocument == null) {
            LOGGER.error("Unable to find Classic Game with ID: {}", gameID);
            throw new jakarta.ws.rs.NotFoundException("Unable to find Classic Game with ID: " + gameID);
        }
        LOGGER.info("Found Classic Game Info: {}", gameDocument);
        return gameDocument;
    }

    public Document findDraftCheck(String gameID, String playerName) {
        Bson filter = Filters.eq("gameID", gameID);

        Document playerVar = new Document("player", new Document(
            "$arrayElemAt", java.util.List.of(
                new Document("$filter", new Document()
                    .append("input", "$players")
                    .append("as", "p")
                    .append("cond", new Document("$eq", java.util.List.of("$$p.playerName", playerName)))
                ), 0
            )
        ));

        Document inExpr = new Document("$ifNull", java.util.List.of("$$player.activeCardPacks", java.util.List.of()));

        Bson projectStage = Aggregates.project(Projections.fields(
            Projections.excludeId(),
            Projections.include("gameState"),
            Projections.computed("canDraft", new Document("$gt", java.util.List.of(
                new Document("$size", new Document("$let", new Document()
                    .append("vars", playerVar)
                    .append("in", inExpr)
                )),
                0
            )))
        ));

        return getRawCollection().aggregate(java.util.List.of(
            Aggregates.match(filter),
            projectStage
        )).first();
    }

    public ClassicPlayer updatePlayer(final ClassicGameInfo gameInfo, final ClassicPlayer player) {
        Bson filter = Filters.and(
                Filters.eq("gameID", gameInfo.getGameID()),
                Filters.eq("players.draftOrderNumber", player.getDraftOrderNumber())
        );

        Bson updateOperation = Updates.set("players.$", player);

        UpdateResult result = getCollection().updateOne(filter, updateOperation);

        if (result.getModifiedCount() > 0) {
            LOGGER.info("Successfully updated Player: {} in Game: {}", player.getPlayerName(), gameInfo.getGameID());
            return player;
        } else {
            LOGGER.error("Unable to update Game {}, with Player {}.", gameInfo.getGameID(), player.getPlayerName());
            throw new IllegalStateException("Unable to update Classic Game with player's new info.");
        }
    }

    public ClassicGameInfo updateGame(final ClassicGameInfo gameInfo) {
        Bson filter = Filters.eq("gameID", gameInfo.getGameID());

        Bson update = Updates.combine(
            Updates.set("players", gameInfo.getPlayers()),
            Updates.set("gameState", gameInfo.getGameState()),
            Updates.set("currentPackIndex", gameInfo.getCurrentPackIndex()),
            Updates.set("draftDirection", gameInfo.getDraftDirection())
        );

        UpdateResult result = getCollection().updateOne(filter, update);

        if (result.getModifiedCount() > 0) {
            LOGGER.info("Successfully updated Classic Game: {}", gameInfo.getGameID());
            return gameInfo;
        } else {
            LOGGER.error("Unable to update Classic Game {}.", gameInfo.getGameID());
            throw new IllegalStateException("Unable to update Classic Game with new info.");
        }
    }

    public ClassicGameState updateGameState(final String gameID, final ClassicGameState gameState) {
        Bson filter = Filters.eq("gameID", gameID);

        Bson update = Updates.combine(
            Updates.set("gameState", gameState));

        UpdateResult result = getCollection().updateOne(filter, update);

        if (result.getModifiedCount() > 0) {
            LOGGER.info("Successfully updated Classic Game state: {} -> {}", gameID, gameState);
            return gameState;
        } else {
            LOGGER.error("Unable to update Classic Game {} state.", gameID);
            throw new IllegalStateException("Unable to update Classic Game state.");
        }
    }

    public int clearGamesWithStatus(final ClassicGameState gameState) {
        Bson filter = Filters.eq("gameState", gameState);
        DeleteResult result = getCollection().deleteMany(filter);
        return (int) result.getDeletedCount();
    }

    public java.util.List<ClassicGameInfo> findGamesByPlayerName(final String playerName) {
        Bson filter = Filters.eq("players.playerName", playerName);

        return getCollection()
                .find(filter)
                .sort(Sorts.descending("createdAt"))
                .into(new ArrayList<>());
    }

    public java.util.List<ClassicGameInfo> findGamesByAccountID(final String accountID) {
        Bson filter = Filters.eq("players.accountID", accountID);

        return getCollection()
                .find(filter)
                .sort(Sorts.descending("createdAt"))
                .into(new ArrayList<>());
    }
}
