package org.magic.lobbyService.app;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.conversions.Bson;
import org.magic.lobbyService.api.Lobby;
import org.magic.lobbyService.api.LobbyStatus;
import org.magic.pyramidDraft.app.GameCoordination.MongoService;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * MongoDB data access handler for lobbies. Provides CRUD operations
 * for {@link Lobby} documents.
 */
@ApplicationScoped
public class LobbyDbHandler {
    private static final Logger LOGGER = LogManager.getLogger(LobbyDbHandler.class);
    private static final String COLLECTION_NAME = "Lobbies";

    @Inject
    MongoService mongoService;

    private MongoCollection<Lobby> getCollection() {
        MongoDatabase database = mongoService.getDatabase();
        return database.getCollection(COLLECTION_NAME, Lobby.class);
    }

    /**
     * Persists a new lobby to the database.
     *
     * @param lobby the lobby to insert
     */
    public void insertLobby(final Lobby lobby) {
        getCollection().insertOne(lobby);
        LOGGER.info("Created lobby: {}", lobby.getLobbyCode());
    }

    /**
     * Finds a lobby by its lobby code.
     *
     * @param lobbyCode the lobby code to search for
     * @return the {@link Lobby} document
     * @throws IllegalStateException if no lobby is found with the given code
     */
    public Lobby findByCode(final String lobbyCode) {
        Bson filter = Filters.eq("_id", lobbyCode);
        Lobby lobby = getCollection().find(filter).first();

        if (lobby == null) {
            LOGGER.error("Unable to find lobby with code: {}", lobbyCode);
            throw new IllegalStateException("Unable to find lobby with code: " + lobbyCode);
        }
        return lobby;
    }

    /**
     * Checks whether a lobby with the given code already exists.
     *
     * @param lobbyCode the lobby code to check
     * @return {@code true} if a lobby with that code exists
     */
    public boolean lobbyExists(final String lobbyCode) {
        Bson filter = Filters.eq("_id", lobbyCode);
        return getCollection().find(filter).first() != null;
    }

    /**
     * Replaces the entire lobby document.
     *
     * @param lobby the lobby with updated fields
     * @throws IllegalStateException if the update fails
     */
    public void updateLobby(final Lobby lobby) {
        Bson filter = Filters.eq("_id", lobby.getLobbyCode());
        var result = getCollection().replaceOne(filter, lobby);

        if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
            LOGGER.error("Unable to update lobby: {}", lobby.getLobbyCode());
            throw new IllegalStateException("Unable to update lobby: " + lobby.getLobbyCode());
        }
    }

    /**
     * Removes a lobby from the database.
     *
     * @param lobbyCode the lobby code to delete
     */
    public void deleteLobby(final String lobbyCode) {
        Bson filter = Filters.eq("_id", lobbyCode);
        getCollection().deleteOne(filter);
        LOGGER.info("Deleted lobby: {}", lobbyCode);
    }

    /**
     * Finds all lobbies with the given status.
     *
     * @param status the status to filter by
     * @return the list of matching lobbies
     */
    public List<Lobby> findLobbiesByStatus(final LobbyStatus status) {
        Bson filter = Filters.eq("status", status);
        return getCollection().find(filter).into(new ArrayList<>());
    }
}
