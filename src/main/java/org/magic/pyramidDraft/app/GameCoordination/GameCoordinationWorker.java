package org.magic.pyramidDraft.app.GameCoordination;

import java.time.Instant;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.GameState;
import org.magic.pyramidDraft.api.GameStatusMessage;
import org.magic.pyramidDraft.api.GameSummary;
import org.magic.pyramidDraft.api.Player;
import org.magic.pyramidDraft.api.PlayerCreationInfo;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardPack;
import org.magic.pyramidDraft.app.CubeDownloader;
import org.magic.pyramidDraft.app.PackCreator;
import org.magic.pyramidDraft.app.PackMerger;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/**
 * Central coordinator for pyramid draft games. Manages the full game lifecycle:
 * starting games, processing card drafts, merging packs between players,
 * and ending games. Delegates cube downloading to {@link CubeDownloader},
 * pack creation to {@link PackCreator}, and pack merging to {@link PackMerger}.
 */
@ApplicationScoped
public class GameCoordinationWorker {
    private static final Logger LOGGER = LogManager.getLogger(GameCoordinationWorker.class);

    private final PyramidDraftDbHandler dbHandler;
    private final PackMerger packMerger;
    private final CubeDownloader cubeDownloader;

    @Inject
    public GameCoordinationWorker(final PyramidDraftDbHandler dbHandler,
                                  final PackMerger packMerger,
                                  final CubeDownloader cubeDownloader) {
        this.dbHandler = dbHandler;
        this.packMerger = packMerger;
        this.cubeDownloader = cubeDownloader;
    }

    /**
     * Starts a new pyramid draft game. Downloads the cube, creates packs for both players,
     * and persists the initial game state to the database.
     *
     * @param gameCreationInfo the creation parameters including cube ID and player info
     * @return a {@link Uni} emitting the created {@link GameInfo}
     */
    public Uni<GameInfo> startGame(final GameCreationInfo gameCreationInfo) {

        PlayerCreationInfo player1Creation = gameCreationInfo.playerInfo().get(0);
        PlayerCreationInfo player2Creation = gameCreationInfo.playerInfo().get(1);

        return cubeDownloader.getCubeForCubeID(gameCreationInfo.cubeID())
                             .map(cube -> new PackCreator(cube))
                             .map(packCreator -> packCreator.createPyramidPacks(player1Creation, 
                                                                                player2Creation,
                                                                                gameCreationInfo.numberOfDoubleDraftPicksPerPlayer()))
                             .map(players -> new GameInfo(gameCreationInfo.gameID(),
                                                          gameCreationInfo.cubeID(),
                                                          players,
                                                          GameState.GAME_STARTED,
                                                          Instant.now()))
                             .invoke(gameInfo -> dbHandler.addGame(gameInfo));
    }

    /**
     * Processes a card draft action for a player. Finds the game and player, then
     * delegates the draft logic to {@link Player#draftCard(String, int, boolean)}.
     *
     * @param accountID  the account drafting the card
     * @param packNumber the pack number to draft from
     * @param cardID     the Scryfall ID of the card being drafted
     * @param isDoublePick whether this draft uses a double-pick token
     * @param gameID     the game to draft from
     * @return the drafted {@link Card}
     */
    public Card draftCard(final String accountID, 
                          final int packNumber, 
                          final String cardID, 
                          final boolean isDoublePick,
                          final String gameID) {

        LOGGER.info("Fetching Game with ID: {}", gameID);
        GameInfo gameInfo = dbHandler.findGame(gameID);
        
        LOGGER.info("\nRetrieved Game: {}\n", gameID);
        final Player player = gameInfo.getPlayers()
                                       .stream()
                                       .filter(pl -> pl.getAccountID().equals(accountID))
                                       .findFirst().get();
        LOGGER.info("\nDrafting {} Card for {}\n", cardID, player.getPlayerName());
        
        Card cardDrafted = player.draftCard(cardID, packNumber, isDoublePick);

        LOGGER.info("\nSuccesfully Allocated Draft for Card: {}\n", cardDrafted.getName());

        dbHandler.updatePlayer(gameInfo, player);
        
        return cardDrafted;
    }

    /**
     * Retrieves the current state of a game.
     *
     * @param gameID the game to look up
     * @return a {@link Uni} emitting the {@link GameInfo}
     */
    public Uni<GameInfo> getGameInfo(final String gameID) {
        return Uni.createFrom().item(dbHandler.findGame(gameID));
    }

    /**
     * Merges and swaps packs between two players. After both players have drafted all
     * their packs, their remaining packs are merged (combining same-size packs) and then
     * swapped — player 1 receives player 2's merged packs and vice versa.
     *
     * <p>The merge only proceeds if both players are ready and the game hasn't already been merged.
     * Each player's draft counters are reset after the swap so they can draft again.</p>
     *
     * @param gameID the game to merge
     * @return a {@link Uni} emitting a {@link GameStatusMessage} with the resulting state
     */
    public Uni<GameStatusMessage> mergeAndSwapPacks(final String gameID) {

        final GameInfo game = dbHandler.findGame(gameID);

        if (game.getPlayers().size() != 2) {
            throw new IllegalStateException("Game has more than 2 players. Cannot Merge and Swap Cards");
        }

        final Player player1 = game.getPlayers().get(0);
        final Player player2 = game.getPlayers().get(1);

        // TODO: Add a lock on a Game when it is being checked for merging since it is shared by two instances on the front

        if (game.getGameState() == GameState.GAME_MERGED) {
            LOGGER.info("Game was already Merged: {}", gameID);
            return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_MERGED));
        }

        // Both players must have completed their draft rounds before merging
        if (player1.isReadyForMerge() && player2.isReadyForMerge() && game.getGameState() != GameState.GAME_MERGED) {
            LOGGER.info("Game Merging: {}", gameID);
            // Merge each player's packs, then swap: player1 gets player2's packs and vice versa
            List<CardPack> mergedPlayer1Packs = packMerger.mergePlayerPacks(player1);
            List<CardPack> mergedPlayer2Packs = packMerger.mergePlayerPacks(player2);
            player1.setCardPacks(mergedPlayer2Packs);
            player2.setCardPacks(mergedPlayer1Packs);

            player1.resetAfterMerge();
            player2.resetAfterMerge();

            game.setGameState(GameState.GAME_MERGED);
            game.updatePlayers(List.of(player1, player2));

            LOGGER.info("Game Object Updating to: {}", game);

            dbHandler.updateGame(game);

            LOGGER.info("Game was Merged: {}", gameID);
            return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_MERGED));
        }
        LOGGER.info("Game {} is not ready for merge, or has already been merged.\nPlayer1: {}\nPlayer2: {}", gameID, 
                                                                                                                        player1.isReadyForMerge(),
                                                                                                                        player2.isReadyForMerge());
        return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_STARTED));
    }

    /**
     * Ends a game by transitioning its state to {@link GameState#GAME_COMPLETE}.
     * If the game is already complete, no database update is performed.
     *
     * @param gameID the game to end
     * @return a {@link Uni} emitting a {@link GameStatusMessage} with GAME_COMPLETE
     */
    public Uni<GameStatusMessage> endGame(String gameID) {

        // very dirty way to check if game state was already updated to complete
        if (!(dbHandler.findGame(gameID).getGameState() == GameState.GAME_COMPLETE)) {
            final GameState gameState = dbHandler.updateGameState(gameID, GameState.GAME_COMPLETE);
            return Uni.createFrom().item(new GameStatusMessage(gameID, gameState));
        }

        return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_COMPLETE));
    }

    /**
     * Deletes all games matching the given state. Administrative endpoint for cleanup.
     *
     * @param gameState the state of games to delete
     * @return a {@link Uni} emitting a 200 OK response with the deletion count
     */
    public Uni<Response> deleteGamesWithStatus(final GameState gameState) {
        final int recordsDeleted = dbHandler.clearGamesWithStatus(gameState);

        final String message = String.format("Deleted %d records with game state of %s.", recordsDeleted, gameState);

        return Uni.createFrom().item(Response.ok(message).build());
    }

    /**
     * Fetches the game history for an account. Returns summaries of all games
     * where the account is a player, sorted newest first.
     *
     * @param accountID the account to look up games for
     * @return a {@link Uni} emitting the list of {@link GameSummary} objects
     */
    public Uni<List<GameSummary>> getGameHistory(final String accountID) {
        List<GameInfo> games = dbHandler.findGamesByAccountID(accountID);

        List<GameSummary> summaries = games.stream()
                .map(game -> {
                    List<Player> players = game.getPlayers();
                    String player1Name = players.size() > 0 ? players.get(0).getPlayerName() : null;
                    String player2Name = players.size() > 1 ? players.get(1).getPlayerName() : null;
                    return new GameSummary(
                            game.getGameID(),
                            game.getCubeID(),
                            game.getGameState(),
                            player1Name,
                            player2Name,
                            game.getCreatedAt()
                    );
                })
                .toList();

        return Uni.createFrom().item(summaries);
    }
}