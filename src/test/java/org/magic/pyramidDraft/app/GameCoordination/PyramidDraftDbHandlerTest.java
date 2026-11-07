package org.magic.pyramidDraft.app.GameCoordination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.common.game.GameState;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.Player;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import io.quarkus.mongodb.FindOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class PyramidDraftDbHandlerTest {

    @Mock
    ReactiveMongoCollection<GameInfo> collection;

    PyramidDraftDbHandler handler;

    @BeforeEach
    void setup() {
        handler = spy(new PyramidDraftDbHandler());
        doReturn(collection).when(handler).mongoCollection();
    }

    private static GameInfo createGame(String id, GameState state) {
        return new GameInfo(id, "cube", "pyramid", List.of(), state, Instant.now());
    }

    private static Uni<UpdateResult> modified(long modifiedCount) {
        return Uni.createFrom().item(UpdateResult.acknowledged(1L, modifiedCount, null));
    }

    @Test
    void shouldAddGame() {
        var gameInfo = createGame("gid", GameState.GAME_STARTED);
        when(collection.insertOne(gameInfo)).thenReturn(Uni.createFrom().item(
                InsertOneResult.acknowledged(new BsonObjectId(new ObjectId("507f1f77bcf86cd799439011")))));

        String result = handler.addGame(gameInfo).await().indefinitely();

        assertEquals("507f1f77bcf86cd799439011", result);
    }

    @Test
    void shouldFindGame() {
        var gameInfo = createGame("gid", GameState.GAME_STARTED);
        when(collection.find(any(Bson.class))).thenReturn(Multi.createFrom().item(gameInfo));

        GameInfo result = handler.findGame("gid").await().indefinitely();

        assertNotNull(result);
        assertEquals("gid", result.getGameID());
    }

    @Test
    void shouldThrowWhenGameNotFound() {
        when(collection.find(any(Bson.class))).thenReturn(Multi.createFrom().empty());

        assertThrows(IllegalStateException.class, () -> handler.findGame("missing").await().indefinitely());
    }

    @Test
    void shouldUpdatePlayer() {
        var player = mock(Player.class);
        var gameInfo = createGame("gid", GameState.GAME_STARTED);

        when(player.getAccountID()).thenReturn("pid");
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(modified(1L));

        Player result = handler.updatePlayer(gameInfo, player).await().indefinitely();

        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenUpdatePlayerFails() {
        var player = mock(Player.class);
        var gameInfo = createGame("gid", GameState.GAME_STARTED);

        when(player.getAccountID()).thenReturn("pid");
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(modified(0L));

        assertThrows(IllegalStateException.class,
                () -> handler.updatePlayer(gameInfo, player).await().indefinitely());
    }

    @Test
    void shouldUpdateGame() {
        var gameInfo = createGame("gid", GameState.GAME_MERGED);
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(modified(1L));

        GameInfo result = handler.updateGame(gameInfo).await().indefinitely();

        assertEquals(GameState.GAME_MERGED, result.getGameState());
    }

    @Test
    void shouldThrowWhenUpdateGameFails() {
        var gameInfo = createGame("gid", GameState.GAME_MERGED);
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(modified(0L));

        assertThrows(IllegalStateException.class,
                () -> handler.updateGame(gameInfo).await().indefinitely());
    }

    @Test
    void shouldUpdateGameState() {
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(modified(1L));

        GameState result = handler.updateGameState("gid", GameState.GAME_COMPLETE).await().indefinitely();

        assertEquals(GameState.GAME_COMPLETE, result);
    }

    @Test
    void shouldThrowWhenUpdateGameStateFails() {
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(modified(0L));

        assertThrows(IllegalStateException.class,
                () -> handler.updateGameState("gid", GameState.GAME_COMPLETE).await().indefinitely());
    }

    @Test
    void shouldClearGamesWithStatus() {
        when(collection.deleteMany(any(Bson.class)))
                .thenReturn(Uni.createFrom().item(DeleteResult.acknowledged(3)));

        int result = handler.clearGamesWithStatus(GameState.GAME_COMPLETE).await().indefinitely();

        assertEquals(3, result);
    }

    @Test
    void shouldDeleteGameWhenFound() {
        when(collection.deleteOne(any(Bson.class)))
                .thenReturn(Uni.createFrom().item(DeleteResult.acknowledged(1)));

        assertTrue(handler.deleteGame("gid").await().indefinitely());
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingGame() {
        when(collection.deleteOne(any(Bson.class)))
                .thenReturn(Uni.createFrom().item(DeleteResult.acknowledged(0)));

        assertFalse(handler.deleteGame("missing").await().indefinitely());
    }

    @Test
    void shouldFindGamesByAccountID() {
        var games = List.of(createGame("g1", GameState.GAME_COMPLETE));
        when(collection.find(any(Bson.class), any(FindOptions.class)))
                .thenReturn(Multi.createFrom().iterable(games));

        List<GameInfo> result = handler.findGamesByAccountID("pid").await().indefinitely();

        assertEquals(1, result.size());
    }
}
