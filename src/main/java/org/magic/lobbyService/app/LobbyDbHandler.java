package org.magic.lobbyService.app;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.common.mongo.ReactiveMongo;
import org.magic.lobbyService.api.Lobby;
import org.magic.lobbyService.api.LobbyStatus;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive repository for lobbies. The lobby code is the document ID.
 */
@ApplicationScoped
public class LobbyDbHandler implements ReactivePanacheMongoRepositoryBase<Lobby, String> {
    private static final Logger LOGGER = LogManager.getLogger(LobbyDbHandler.class);

    public Uni<Void> insertLobby(final Lobby lobby) {
        return persist(lobby)
                .invoke(ignored -> LOGGER.info("Created lobby: {}", lobby.getLobbyCode()))
                .replaceWithVoid();
    }

    public Uni<Lobby> findByCode(final String lobbyCode) {
        return ReactiveMongo.requiredOptional(findByIdOptional(lobbyCode), () -> {
            LOGGER.warn("Unable to find lobby with code: {}", lobbyCode);
            return new IllegalStateException("Unable to find lobby with code: " + lobbyCode);
        });
    }

    public Uni<Boolean> lobbyExists(final String lobbyCode) {
        return findByIdOptional(lobbyCode).map(java.util.Optional::isPresent);
    }

    /**
     * Replaces the whole lobby document. Rejects updates that matched nothing,
     * preserving the historical "lobby must exist" contract.
     */
    public Uni<Void> updateLobby(final Lobby lobby) {
        return mongoCollection().replaceOne(new org.bson.Document("_id", lobby.getLobbyCode()), lobby)
                .map(result -> {
                    if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
                        LOGGER.error("Unable to update lobby: {}", lobby.getLobbyCode());
                        throw new IllegalStateException("Unable to update lobby: " + lobby.getLobbyCode());
                    }
                    return result;
                })
                .replaceWithVoid();
    }

    public Uni<Void> deleteLobby(final String lobbyCode) {
        return deleteById(lobbyCode)
                .invoke(ignored -> LOGGER.info("Deleted lobby: {}", lobbyCode))
                .replaceWithVoid();
    }

    public Uni<List<Lobby>> findLobbiesByStatus(final LobbyStatus status) {
        return list("status = ?1", status);
    }
}
