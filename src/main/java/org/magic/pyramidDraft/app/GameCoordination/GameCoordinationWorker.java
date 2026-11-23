package org.magic.pyramidDraft.app.GameCoordination;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.common.card.Card;
import org.magic.common.card.CardPack;
import org.magic.common.cube.CubeDownloader;
import org.magic.common.game.GameState;
import org.magic.common.game.GameStatusMessage;
import org.magic.pyramidDraft.api.GameCreationInfo;
import org.magic.pyramidDraft.api.GameInfo;
import org.magic.pyramidDraft.api.Player;
import org.magic.pyramidDraft.api.PlayerCreationInfo;
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
 *
 * <p>All persistence is reactive: the game state is read and written through
 * {@link PyramidDraftDbHandler}'s {@link Uni} API, so no event-loop thread is
 * ever blocked. Pure in-memory work (pack creation, merging, draft rules)
 * runs inline on the completing thread.</p>
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
                             .map(players -> {
                                 for (Player p : players) {
                                     if (p.getPlayerToken() == null || p.getPlayerToken().isBlank()) {
                                         p.setPlayerToken(UUID.randomUUID().toString());
                                     }
                                 }
                                 return players;
                             })
                             .map(players -> new GameInfo(gameCreationInfo.gameID(),
                                                          gameCreationInfo.cubeID(),
                                                          "pyramid",
                                                          players,
                                                          GameState.GAME_STARTED,
                                                          Instant.now()))
                             .call(gameInfo -> dbHandler.addGame(gameInfo));
    }

    /**
     * Processes a card draft action for a player. Finds the game and player, then
     * delegates the draft logic to {@link Player#draftCard(String, int, boolean)}.
     *
     * @param playerToken  the drafting player's token
     * @param packNumber   the pack number to draft from
     * @param cardID       the Scryfall ID of the card being drafted
     * @param isDoublePick whether this draft uses a double-pick token
     * @param gameID       the game to draft from
     * @return a {@link Uni} emitting the drafted {@link Card}
     */
    public Uni<Card> draftCard(final String playerToken,
                               final int packNumber,
                               final String cardID,
                               final boolean isDoublePick,
                               final String gameID) {

        LOGGER.info("Fetching Game with ID: {}", gameID);
        return dbHandler.findGame(gameID)
                .chain(gameInfo -> {
                    LOGGER.info("\nRetrieved Game: {}\n", gameID);
                    final Player player = gameInfo.getPlayers()
                                                   .stream()
                                                   .filter(pl -> playerToken.equals(pl.getPlayerToken()))
                                                   .findFirst()
                                                   .orElseThrow(() -> new IllegalArgumentException("Player not found"));
                    LOGGER.info("\nDrafting {} Card for {}\n", cardID, player.getPlayerName());

                    final Card cardDrafted = player.draftCard(cardID, packNumber, isDoublePick);

                    LOGGER.info("\nSuccesfully Allocated Draft for Card: {}\n", cardDrafted.getName());

                    return dbHandler.updatePlayer(gameInfo, player)
                            .chain(ignored -> {
                                if (gameInfo.getGameState() == GameState.GAME_MERGED) {
                                    Player p1 = gameInfo.getPlayers().get(0);
                                    Player p2 = gameInfo.getPlayers().get(1);
                                    if (p1.isReadyForMerge() && p2.isReadyForMerge()) {
                                        LOGGER.info("Both players finished post-merge. Auto-completing game: {}", gameID);
                                        return dbHandler.updateGameState(gameID, GameState.GAME_COMPLETE)
                                                .replaceWith(cardDrafted);
                                    }
                                }
                                return Uni.createFrom().item(cardDrafted);
                            });
                });
    }

    /**
     * Retrieves the current state of a game.
     *
     * @param gameID the game to look up
     * @return a {@link Uni} emitting the {@link GameInfo}
     */
    public Uni<GameInfo> getGameInfo(final String gameID) {
        return dbHandler.findGame(gameID);
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

        return dbHandler.findGame(gameID)
                .chain(game -> {
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
                    if (player1.isReadyForMerge() && player2.isReadyForMerge()) {
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

                        LOGGER.info("Merging game: {}", gameID);
                        return dbHandler.updateGame(game)
                                .invoke(ignored -> LOGGER.info("Game was Merged: {}", gameID))
                                .replaceWith(new GameStatusMessage(gameID, GameState.GAME_MERGED));
                    }
                    LOGGER.info("Game {} is not ready for merge, or has already been merged.\nPlayer1: {}\nPlayer2: {}",
                            gameID, player1.isReadyForMerge(), player2.isReadyForMerge());
                    return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_STARTED));
                });
    }

    /**
     * Ends a game by transitioning its state to {@link GameState#GAME_COMPLETE}.
     * If the game is already complete, no database update is performed.
     *
     * @param gameID the game to end
     * @return a {@link Uni} emitting a {@link GameStatusMessage} with GAME_COMPLETE
     */
    public Uni<GameStatusMessage> endGame(final String gameID) {
        return dbHandler.findGame(gameID)
                .chain(game -> {
                    if (game.getGameState() == GameState.GAME_COMPLETE) {
                        return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_COMPLETE));
                    }
                    return dbHandler.updateGameState(gameID, GameState.GAME_COMPLETE)
                            .map(gameState -> new GameStatusMessage(gameID, gameState));
                });
    }

    /**
     * Deletes all games matching the given state. Administrative endpoint for cleanup.
     *
     * @param gameState the state of games to delete
     * @return a {@link Uni} emitting a 200 OK response with the deletion count
     */
    public Uni<Response> deleteGamesWithStatus(final GameState gameState) {
        return dbHandler.clearGamesWithStatus(gameState)
                .map(recordsDeleted -> {
                    final String message = String.format(
                            "Deleted %d records with game state of %s.", recordsDeleted, gameState);
                    return Response.ok(message).build();
                });
    }

    /**
     * Deletes a single game by its ID. Administrative cleanup utility.
     * Players, packs, and drafted cards are embedded in the game document,
     * so they are removed along with the game.
     *
     * @param gameID the game to delete
     * @return a {@link Uni} emitting 204 No Content if deleted, 404 if not found
     */
    public Uni<Response> deleteGame(final String gameID) {
        LOGGER.info("Deleting Game: {}", gameID);
        return dbHandler.deleteGame(gameID)
                .map(deleted -> deleted
                        ? Response.noContent().build()
                        : Response.status(Response.Status.NOT_FOUND).build());
    }
}
