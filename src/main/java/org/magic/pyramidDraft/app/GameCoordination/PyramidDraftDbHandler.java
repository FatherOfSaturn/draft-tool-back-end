package org.magic.pyramidDraft.app.GameCoordination;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.conversions.Bson;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.api.Player;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * MongoDB data access handler for pyramid draft games. Provides CRUD operations
 * for {@link GameInfo} documents, including player updates and bulk deletion by game state.
 */
@ApplicationScoped
public class PyramidDraftDbHandler {
    private static final Logger LOGGER = LogManager.getLogger(PyramidDraftDbHandler.class);

    @Inject
    MongoService mongoService;

    public MongoCollection<GameInfo> getCollection() {
        MongoDatabase database = mongoService.getDatabase();
        return database.getCollection("Games", GameInfo.class);
    }

    /**
     * Persists a new game to the database.
     *
     * @param gameInfo the game to insert
     * @return the MongoDB-generated ObjectId as a hex string
     */
    public String addGame(GameInfo gameInfo) {
        return getCollection().insertOne(gameInfo).getInsertedId().asObjectId().getValue().toHexString();
    }

    /**
     * Finds a game by its ID.
     *
     * @param gameID the game ID to search for
     * @return the {@link GameInfo} document
     * @throws IllegalStateException if no game is found with the given ID
     */
    public GameInfo findGame(String gameID) {

        // Create a filter to match the gameID
        Bson filter = Filters.eq("gameID", gameID);
        GameInfo gameDocument = getCollection().find(filter).first();

        if (gameDocument == null) {
            LOGGER.error("Unable to find Game with ID: {}", gameID);
            throw new IllegalStateException("Unable to find Game with ID: " + gameID);
        }
        else {
            LOGGER.info("Found Game Info: {}", gameDocument);
        }
        return gameDocument;
    }

    /**
     * Updates a specific player within a game. Uses MongoDB's positional operator ($)
     * to update only the matching player in the players array.
     *
     * @param gameInfo the game containing the player
     * @param player   the updated player data
     * @return the updated player
     * @throws IllegalStateException if the update fails
     */
    public Player updatePlayer(final GameInfo gameInfo, final Player player) {

        // Create a filter to match the document by gameID and nested accountID
        Bson filter = Filters.and(
                Filters.eq("gameID", gameInfo.getGameID()),
                Filters.eq("players.accountID", player.getAccountID())
        );

        Bson updateOperation = Updates.set("players.$", player);

        // Perform the update
        UpdateResult result = getCollection().updateOne(filter, updateOperation);

        if (result.getModifiedCount() > 0) {
            LOGGER.info("Successfully updated Player: {}\n\tFor Game: {}", player.getAccountID(), gameInfo.getGameID());
            return player;
        } else {
            LOGGER.error("Unable to update Game {}, with Player {}, new info.", gameInfo.getGameID(), player.getAccountID());
            throw new IllegalStateException("Unable to Update Game with players new info.");
        }
    }

    /**
     * Updates the players list and game state for a game.
     *
     * @param gameInfo the game with updated data
     * @return the updated game
     * @throws IllegalStateException if the update fails
     */
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
            throw new IllegalStateException("Unable to Update Game with new info.");
        }
    }

    /**
     * Updates only the game state for a game.
     *
     * @param gameID    the game to update
     * @param gameState the new state
     * @return the updated state
     * @throws IllegalStateException if the update fails
     */
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
            throw new IllegalStateException("Unable to Update Game with new info.");
        }
    }

    /**
     * Deletes all games matching the given state.
     *
     * @param gameState the state to filter by
     * @return the number of deleted documents
     */
    public int clearGamesWithStatus(final GameState gameState) {
        Bson filter = Filters.eq("gameState", gameState);

        DeleteResult result = this.getCollection().deleteMany(filter);
        
        return (int) result.getDeletedCount();
    }

    /**
     * Finds all games where the given account is a player.
     * Results are sorted by creation date descending (newest first).
     *
     * @param accountID the account ID to search for
     * @return the list of matching games
     */
    public java.util.List<GameInfo> findGamesByAccountID(final String accountID) {
        Bson filter = Filters.eq("players.accountID", accountID);

        return getCollection()
                .find(filter)
                .sort(Sorts.descending("createdAt"))
                .into(new java.util.ArrayList<>());
    }
}