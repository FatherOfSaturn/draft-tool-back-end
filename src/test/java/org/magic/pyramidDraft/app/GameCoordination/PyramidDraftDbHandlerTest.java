package org.magic.pyramidDraft.app.GameCoordination;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.api.Player;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

@ExtendWith(MockitoExtension.class)
class PyramidDraftDbHandlerTest {

    @Mock
    MongoService mongoService;
    @Mock
    MongoDatabase database;
    @Mock
    MongoCollection<GameInfo> collection;
    @Mock
    FindIterable<GameInfo> findIterable;
    @Mock
    InsertOneResult insertResult;
    @Mock
    UpdateResult updateResult;
    @Mock
    DeleteResult deleteResult;

    PyramidDraftDbHandler handler;

    @BeforeEach
    void setup() {
        when(mongoService.getDatabase()).thenReturn(database);
        when(database.getCollection("Games", GameInfo.class)).thenReturn(collection);
        handler = new PyramidDraftDbHandler();
        handler.mongoService = mongoService;
    }

    private GameInfo createGame(String id, GameState state) {
        return new GameInfo(id, "cube", "pyramid", List.of(), state, Instant.now());
    }

    @Test
    void shouldAddGame() {
        var gameInfo = createGame("gid", GameState.GAME_STARTED);
        var objectId = new ObjectId("507f1f77bcf86cd799439011");
        var bsonObjectId = new BsonObjectId(objectId);

        when(collection.insertOne(gameInfo)).thenReturn(insertResult);
        when(insertResult.getInsertedId()).thenReturn(bsonObjectId);

        String result = handler.addGame(gameInfo);

        assertEquals("507f1f77bcf86cd799439011", result);
    }

    @Test
    void shouldFindGame() {
        var gameInfo = createGame("gid", GameState.GAME_STARTED);

        when(collection.find(any(Bson.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(gameInfo);

        GameInfo result = handler.findGame("gid");

        assertNotNull(result);
        assertEquals("gid", result.getGameID());
    }

    @Test
    void shouldThrowWhenGameNotFound() {
        when(collection.find(any(Bson.class))).thenReturn(findIterable);
        when(findIterable.first()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> handler.findGame("missing"));
    }

    @Test
    void shouldUpdatePlayer() {
        var player = mock(Player.class);
        var gameInfo = createGame("gid", GameState.GAME_STARTED);

        when(player.getAccountID()).thenReturn("pid");
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(1L);

        Player result = handler.updatePlayer(gameInfo, player);

        assertNotNull(result);
    }

    @Test
    void shouldThrowWhenUpdatePlayerFails() {
        var player = mock(Player.class);
        var gameInfo = createGame("gid", GameState.GAME_STARTED);

        when(player.getAccountID()).thenReturn("pid");
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> handler.updatePlayer(gameInfo, player));
    }

    @Test
    void shouldUpdateGame() {
        var gameInfo = createGame("gid", GameState.GAME_MERGED);

        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(1L);

        GameInfo result = handler.updateGame(gameInfo);

        assertEquals(GameState.GAME_MERGED, result.getGameState());
    }

    @Test
    void shouldThrowWhenUpdateGameFails() {
        var gameInfo = createGame("gid", GameState.GAME_MERGED);

        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> handler.updateGame(gameInfo));
    }

    @Test
    void shouldUpdateGameState() {
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(1L);

        GameState result = handler.updateGameState("gid", GameState.GAME_COMPLETE);

        assertEquals(GameState.GAME_COMPLETE, result);
    }

    @Test
    void shouldThrowWhenUpdateGameStateFails() {
        when(collection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(0L);

        assertThrows(IllegalStateException.class, () -> handler.updateGameState("gid", GameState.GAME_COMPLETE));
    }

    @Test
    void shouldClearGamesWithStatus() {
        when(collection.deleteMany(any(Bson.class))).thenReturn(deleteResult);
        when(deleteResult.getDeletedCount()).thenReturn(3L);

        int result = handler.clearGamesWithStatus(GameState.GAME_COMPLETE);

        assertEquals(3, result);
    }

    @Test
    void shouldDeleteGameWhenFound() {
        when(collection.deleteOne(any(Bson.class))).thenReturn(deleteResult);
        when(deleteResult.getDeletedCount()).thenReturn(1L);

        boolean result = handler.deleteGame("gid");

        assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenDeletingMissingGame() {
        when(collection.deleteOne(any(Bson.class))).thenReturn(deleteResult);
        when(deleteResult.getDeletedCount()).thenReturn(0L);

        boolean result = handler.deleteGame("missing");

        assertFalse(result);
    }

    @Test
    void shouldFindGamesByAccountID() {
        var games = List.of(createGame("g1", GameState.GAME_COMPLETE));
        when(collection.find(any(Bson.class))).thenReturn(findIterable);
        when(findIterable.sort(any())).thenReturn(findIterable);
        when(findIterable.into(any())).thenReturn(games);

        List<GameInfo> result = handler.findGamesByAccountID("pid");

        assertEquals(1, result.size());
    }
}
