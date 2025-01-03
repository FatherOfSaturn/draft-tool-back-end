package org.magic.draft.app.GameCoordination;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.magic.draft.api.GameInfo;
import org.magic.draft.api.GameState;
import org.magic.draft.api.Player;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DbHandler {
    private static final Logger LOGGER = LogManager.getLogger(DbHandler.class);

    @Inject
    MongoService mongoService;

    public MongoCollection<GameInfo> getCollection() {
        MongoDatabase database = mongoService.getDatabase();
        return database.getCollection("Games", GameInfo.class);
    }

    public String addGame(GameInfo gameInfo) {
        return getCollection().insertOne(gameInfo).getInsertedId().asObjectId().getValue().toHexString();
    }

    public GameInfo findUser(String id) {
        return getCollection().find(new Document("_id", id)).first();
    }

    public GameInfo findGame(String gameID) {

        // Create a filter to match the gameID
        Bson filter = Filters.eq("gameID", gameID);
        GameInfo gameDocument = getCollection().find(filter).first();

        if (gameDocument == null) {
            LOGGER.error("Unable to find Game with ID: {}", gameID);
            throw new Error("Unable to find Game with ID: " + gameID);
        }
        else {
            LOGGER.info("Found Game Info: {}", gameDocument.toString());
        }
        return gameDocument;
    }

    public Player updatePlayer(final GameInfo gameInfo, final Player player) {

        // Create a filter to match the document by gameID and nested playerID
        Bson filter = Filters.and(
                Filters.eq("gameID", gameInfo.getGameID()),
                Filters.eq("players.playerID", player.getPlayerID())
        );

        Bson updateOperation = Updates.set("players.$", player);

        // Perform the update
        UpdateResult result = getCollection().updateOne(filter, updateOperation);

        if (result.getModifiedCount() > 0) {
            LOGGER.info("Successfully updated Player: {}\n\tFor Game: {}", player.getPlayerID(), gameInfo.getGameID());
            return player;
        } else {
            LOGGER.error("Unable to update Game {}, with Player {}, new info.", gameInfo.getGameID(), player.getPlayerID());
            throw new Error("Unable to Update Game with players new info.");
        }
    }

    public GameInfo updateGame(final GameInfo gameInfo) {

        Bson filter = Filters.eq("gameID", gameInfo.getGameID());

        Bson update = Updates.combine(
            Updates.set("players", gameInfo.getPlayers()),
            Updates.set("gameState", gameInfo.getGameState())
        );

        // Perform the update
        UpdateResult result = getCollection().updateOne(filter, update);

        if (result.getModifiedCount() > 0) {
            LOGGER.info("Successfully updated Game: {}\n", gameInfo.getGameID());
            return gameInfo;
        } else {
            LOGGER.error("Unable to update Game {}, new info.", gameInfo.getGameID());
            throw new Error("Unable to Update Game with new info.");
        }
    }

    public GameState updateGameState(final String gameID, final GameState gameState) {
        
        Bson filter = Filters.eq("gameID", gameID);

        Bson update = Updates.combine(
            Updates.set("gameState", gameState));
        // Perform the update
        UpdateResult result = getCollection().updateOne(filter, update);

        if (result.getModifiedCount() > 0) {
            LOGGER.info("Successfully updated Game: {}\n", gameID);
            return gameState;
        } else {
            LOGGER.error("Unable to update Game {}, with Game State {}.", gameID, gameState);
            throw new Error("Unable to Update Game with new info.");
        }
    }

    public int clearGamesWithStatus(final GameState gameState) {
        Bson filter = Filters.eq("gameState", gameState);

        DeleteResult result = this.getCollection().deleteMany(filter);
        
        return (int) result.getDeletedCount();
    }
}