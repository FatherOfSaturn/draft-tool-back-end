package org.magic.pyramidDraft.app.GameCoordination;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.conversions.Bson;
import org.magic.common.game.GameState;
import org.magic.common.mongo.ReactiveMongo;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.Player;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive repository for pyramid draft games.
 *
 * <p>Games are keyed by the business {@code gameID}, while MongoDB's {@code _id}
 * is driver-generated and deliberately not mapped on the model (the persisted
 * document shape is part of the public contract). Player and state changes are
 * therefore targeted updates: {@code players.$} positional updates avoid
 * overwriting a concurrent draft.</p>
 */
@ApplicationScoped
public class PyramidDraftDbHandler implements ReactivePanacheMongoRepository<GameInfo> {
    private static final Logger LOGGER = LogManager.getLogger(PyramidDraftDbHandler.class);

    public Uni<String> addGame(final GameInfo gameInfo) {
        return mongoCollection().insertOne(gameInfo)
                .map(result -> result.getInsertedId().asObjectId().getValue().toHexString());
    }

    public Uni<GameInfo> findGame(final String gameID) {
        return ReactiveMongo.required(
                find("gameID = ?1", gameID).firstResult(),
                () -> {
                    LOGGER.error("Unable to find Game with ID: {}", gameID);
                    return new IllegalStateException("Unable to find Game with ID: " + gameID);
                });
    }

    public Uni<Player> updatePlayer(final GameInfo gameInfo, final Player player) {
        Bson filter = Filters.and(
                Filters.eq("gameID", gameInfo.getGameID()),
                Filters.eq("players.accountID", player.getAccountID()));
        return ReactiveMongo.modified(
                mongoCollection().updateOne(filter, Updates.set("players.$", player)),
                () -> player,
                () -> {
                    LOGGER.error("Unable to update Game {}, with Player {}, new info.",
                            gameInfo.getGameID(), player.getAccountID());
                    return new IllegalStateException("Unable to Update Game with players new info.");
                });
    }

    public Uni<GameInfo> updateGame(final GameInfo gameInfo) {
        Bson update = Updates.combine(
                Updates.set("players", gameInfo.getPlayers()),
                Updates.set("gameState", gameInfo.getGameState()));
        return ReactiveMongo.modified(
                mongoCollection().updateOne(Filters.eq("gameID", gameInfo.getGameID()), update),
                () -> gameInfo,
                () -> {
                    LOGGER.error("Unable to update Game {}, new info.", gameInfo.getGameID());
                    return new IllegalStateException("Unable to Update Game with new info.");
                });
    }

    public Uni<GameState> updateGameState(final String gameID, final GameState gameState) {
        return ReactiveMongo.modified(
                mongoCollection().updateOne(Filters.eq("gameID", gameID),
                        Updates.set("gameState", gameState)),
                () -> gameState,
                () -> {
                    LOGGER.error("Unable to update Game {}, with Game State {}.", gameID, gameState);
                    return new IllegalStateException("Unable to Update Game with new info.");
                });
    }

    public Uni<Integer> clearGamesWithStatus(final GameState gameState) {
        return delete("gameState = ?1", gameState).map(Long::intValue);
    }

    public Uni<Boolean> deleteGame(final String gameID) {
        return ReactiveMongo.deleted(mongoCollection().deleteOne(Filters.eq("gameID", gameID)))
                .invoke(deleted -> {
                    if (deleted) {
                        LOGGER.info("Deleted Game: {}", gameID);
                    } else {
                        LOGGER.warn("Game not found for delete: {}", gameID);
                    }
                });
    }

    public Uni<List<GameInfo>> findGamesByAccountID(final String accountID) {
        return list("players.accountID = ?1", Sort.descending("createdAt"), accountID);
    }
}
