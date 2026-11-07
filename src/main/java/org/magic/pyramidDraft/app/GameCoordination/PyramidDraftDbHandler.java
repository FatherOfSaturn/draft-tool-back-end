package org.magic.pyramidDraft.app.GameCoordination;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.conversions.Bson;
import org.magic.common.game.GameState;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.Player;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import io.quarkus.mongodb.FindOptions;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive MongoDB data access handler for pyramid draft games. Every operation
 * returns a {@link Uni} backed by the reactive Mongo driver, so callers never
 * block an event-loop thread.
 */
@ApplicationScoped
public class PyramidDraftDbHandler implements ReactivePanacheMongoRepository<GameInfo> {
    private static final Logger LOGGER = LogManager.getLogger(PyramidDraftDbHandler.class);

    /**
     * Persists a new game to the database.
     *
     * @param gameInfo the game to insert
     * @return the MongoDB-generated ObjectId as a hex string
     */
    public Uni<String> addGame(final GameInfo gameInfo) {
        return mongoCollection().insertOne(gameInfo)
                .map(result -> result.getInsertedId().asObjectId().getValue().toHexString());
    }

    /**
     * Finds a game by its ID.
     *
     * @param gameID the game ID to search for
     * @return a {@link Uni} emitting the {@link GameInfo} document
     * @throws IllegalStateException if no game is found with the given ID
     */
    public Uni<GameInfo> findGame(final String gameID) {
        Bson filter = Filters.eq("gameID", gameID);
        return mongoCollection().find(filter).collect().first()
                .onItem().ifNull().failWith(() -> {
                    LOGGER.error("Unable to find Game with ID: {}", gameID);
                    return new IllegalStateException("Unable to find Game with ID: " + gameID);
                })
                .invoke(gameDocument -> LOGGER.info("Found Game Info for game: {}", gameDocument.getGameID()));
    }

    /**
     * Updates a specific player within a game. Uses MongoDB's positional operator ($)
     * to update only the matching player in the players array.
     *
     * @param gameInfo the game containing the player
     * @param player   the updated player data
     * @return a {@link Uni} emitting the updated player
     * @throws IllegalStateException if the update fails
     */
    public Uni<Player> updatePlayer(final GameInfo gameInfo, final Player player) {
        Bson filter = Filters.and(
                Filters.eq("gameID", gameInfo.getGameID()),
                Filters.eq("players.accountID", player.getAccountID())
        );
        Bson updateOperation = Updates.set("players.$", player);

        return mongoCollection().updateOne(filter, updateOperation)
                .map(result -> {
                    if (result.getModifiedCount() > 0) {
                        LOGGER.info("Successfully updated Player: {}\n\tFor Game: {}",
                                player.getAccountID(), gameInfo.getGameID());
                        return player;
                    }
                    LOGGER.error("Unable to update Game {}, with Player {}, new info.",
                            gameInfo.getGameID(), player.getAccountID());
                    throw new IllegalStateException("Unable to Update Game with players new info.");
                });
    }

    /**
     * Updates the players list and game state for a game.
     *
     * @param gameInfo the game with updated data
     * @return a {@link Uni} emitting the updated game
     * @throws IllegalStateException if the update fails
     */
    public Uni<GameInfo> updateGame(final GameInfo gameInfo) {
        Bson filter = Filters.eq("gameID", gameInfo.getGameID());
        Bson update = Updates.combine(
                Updates.set("players", gameInfo.getPlayers()),
                Updates.set("gameState", gameInfo.getGameState())
        );

        return mongoCollection().updateOne(filter, update)
                .map(result -> {
                    if (result.getModifiedCount() > 0) {
                        LOGGER.info("Successfully updated Game: {}\n", gameInfo.getGameID());
                        return gameInfo;
                    }
                    LOGGER.error("Unable to update Game {}, new info.", gameInfo.getGameID());
                    throw new IllegalStateException("Unable to Update Game with new info.");
                });
    }

    /**
     * Updates only the game state for a game.
     *
     * @param gameID    the game to update
     * @param gameState the new state
     * @return a {@link Uni} emitting the updated state
     * @throws IllegalStateException if the update fails
     */
    public Uni<GameState> updateGameState(final String gameID, final GameState gameState) {
        Bson filter = Filters.eq("gameID", gameID);
        Bson update = Updates.set("gameState", gameState);

        return mongoCollection().updateOne(filter, update)
                .map(result -> {
                    if (result.getModifiedCount() > 0) {
                        LOGGER.info("Successfully updated Game: {}\n", gameID);
                        return gameState;
                    }
                    LOGGER.error("Unable to update Game {}, with Game State {}.", gameID, gameState);
                    throw new IllegalStateException("Unable to Update Game with new info.");
                });
    }

    /**
     * Deletes all games matching the given state.
     *
     * @param gameState the state to filter by
     * @return a {@link Uni} emitting the number of deleted documents
     */
    public Uni<Integer> clearGamesWithStatus(final GameState gameState) {
        Bson filter = Filters.eq("gameState", gameState);
        return mongoCollection().deleteMany(filter).map(result -> (int) result.getDeletedCount());
    }

    /**
     * Deletes a single game by its ID. Players, packs, and drafted cards are
     * embedded in the game document, so removing the document removes all
     * dependent data.
     *
     * @param gameID the game ID to delete
     * @return a {@link Uni} emitting {@code true} if the game was deleted
     */
    public Uni<Boolean> deleteGame(final String gameID) {
        Bson filter = Filters.eq("gameID", gameID);
        return mongoCollection().deleteOne(filter)
                .map(result -> {
                    if (result.getDeletedCount() > 0) {
                        LOGGER.info("Deleted Game: {}", gameID);
                        return true;
                    }
                    LOGGER.warn("Game not found for delete: {}", gameID);
                    return false;
                });
    }

    /**
     * Finds all games where the given account is a player, newest first.
     *
     * @param accountID the account ID to search for
     * @return a {@link Uni} emitting the matching games
     */
    public Uni<List<GameInfo>> findGamesByAccountID(final String accountID) {
        Bson filter = Filters.eq("players.accountID", accountID);
        FindOptions options = new FindOptions().sort(Sorts.descending("createdAt"));
        return mongoCollection().find(filter, options).collect().asList();
    }
}
