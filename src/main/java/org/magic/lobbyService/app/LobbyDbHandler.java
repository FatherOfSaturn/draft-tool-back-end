package org.magic.lobbyService.app;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.conversions.Bson;
import org.magic.lobbyService.api.Lobby;
import org.magic.lobbyService.api.LobbyStatus;

import com.mongodb.client.model.Filters;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive MongoDB data access handler for lobbies. Provides CRUD operations
 * for {@link Lobby} documents.
 */
@ApplicationScoped
public class LobbyDbHandler implements ReactivePanacheMongoRepository<Lobby> {
    private static final Logger LOGGER = LogManager.getLogger(LobbyDbHandler.class);

    /**
     * Persists a new lobby to the database.
     *
     * @param lobby the lobby to insert
     */
    public Uni<Void> insertLobby(final Lobby lobby) {
        return mongoCollection().insertOne(lobby)
                .invoke(ignored -> LOGGER.info("Created lobby: {}", lobby.getLobbyCode()))
                .replaceWithVoid();
    }

    /**
     * Finds a lobby by its lobby code.
     *
     * @param lobbyCode the lobby code to search for
     * @return a {@link Uni} emitting the {@link Lobby} document
     * @throws IllegalStateException if no lobby is found with the given code
     */
    public Uni<Lobby> findByCode(final String lobbyCode) {
        Bson filter = Filters.eq("_id", lobbyCode);
        return mongoCollection().find(filter).collect().first()
                .onItem().ifNull().failWith(() -> {
                    LOGGER.warn("Unable to find lobby with code: {}", lobbyCode);
                    return new IllegalStateException("Unable to find lobby with code: " + lobbyCode);
                });
    }

    /**
     * Checks whether a lobby with the given code already exists.
     *
     * @param lobbyCode the lobby code to check
     * @return a {@link Uni} emitting {@code true} if a lobby with that code exists
     */
    public Uni<Boolean> lobbyExists(final String lobbyCode) {
        Bson filter = Filters.eq("_id", lobbyCode);
        return mongoCollection().find(filter).collect().first()
                .map(lobby -> lobby != null);
    }

    /**
     * Replaces the entire lobby document.
     *
     * @param lobby the lobby with updated fields
     * @return a {@link Uni} completing when the replacement is persisted
     * @throws IllegalStateException if the update fails
     */
    public Uni<Void> updateLobby(final Lobby lobby) {
        Bson filter = Filters.eq("_id", lobby.getLobbyCode());
        return mongoCollection().replaceOne(filter, lobby)
                .map(result -> {
                    if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
                        LOGGER.error("Unable to update lobby: {}", lobby.getLobbyCode());
                        throw new IllegalStateException("Unable to update lobby: " + lobby.getLobbyCode());
                    }
                    return result;
                })
                .replaceWithVoid();
    }

    /**
     * Removes a lobby from the database.
     *
     * @param lobbyCode the lobby code to delete
     */
    public Uni<Void> deleteLobby(final String lobbyCode) {
        Bson filter = Filters.eq("_id", lobbyCode);
        return mongoCollection().deleteOne(filter)
                .invoke(ignored -> LOGGER.info("Deleted lobby: {}", lobbyCode))
                .replaceWithVoid();
    }

    /**
     * Finds all lobbies with the given status.
     *
     * @param status the status to filter by
     * @return a {@link Uni} emitting the list of matching lobbies
     */
    public Uni<List<Lobby>> findLobbiesByStatus(final LobbyStatus status) {
        Bson filter = Filters.eq("status", status);
        return mongoCollection().find(filter).collect().asList();
    }
}
