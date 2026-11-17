package org.magic.classicDraft.app.GameCoordination;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.magic.classicDraft.api.ClassicGameCreationInfo;
import org.magic.classicDraft.api.ClassicGameInfo;
import org.magic.classicDraft.api.ClassicGameSummary;
import org.magic.classicDraft.api.ClassicPlayer;
import org.magic.common.game.DraftDirection;
import org.magic.classicDraft.api.DraftPlayerSnapshot;
import org.magic.classicDraft.api.PlayerDraftCheck;
import org.magic.classicDraft.api.PlayerDraftData;
import org.magic.classicDraft.app.ClassicPackCreator;
import org.magic.common.card.Card;
import org.magic.common.card.CardPack;
import org.magic.common.cube.CubeDownloader;
import org.magic.common.game.GameState;
import org.magic.common.game.GameStatusMessage;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Reactive coordinator for classic (rotisserie) draft games. Persistence goes
 * through {@link ClassicDraftDbHandler}'s {@link Uni} API; pack passing,
 * generation advancement and completion checks are in-memory work performed
 * inline on the completing thread.
 */
@ApplicationScoped
public class ClassicGameCoordinationWorker {
    private static final Logger LOGGER = LogManager.getLogger(ClassicGameCoordinationWorker.class);

    private final ClassicDraftDbHandler dbHandler;
    private final CubeDownloader cubeDownloader;

    @Inject
    public ClassicGameCoordinationWorker(final ClassicDraftDbHandler dbHandler,
                                          final CubeDownloader cubeDownloader) {
        this.dbHandler = dbHandler;
        this.cubeDownloader = cubeDownloader;
    }

    public Uni<ClassicGameInfo> startGame(final ClassicGameCreationInfo creationInfo) {
        String gameID = UUID.randomUUID().toString();
        return cubeDownloader.getCubeForCubeID(creationInfo.cubeID())
                .map(cube -> new ClassicPackCreator(cube, creationInfo.cardsPerPack(), creationInfo.packsPerPlayer()))
                .map(packCreator -> packCreator.createClassicPacks(creationInfo.players()))
                .map(players -> {
                    for (ClassicPlayer p : players) {
                        if (p.getPlayerToken() == null || p.getPlayerToken().isBlank()) {
                            p.setPlayerToken(UUID.randomUUID().toString());
                        }
                    }
                    return players;
                })
                .map(players -> {
                    for (ClassicPlayer p : players) {
                        p.getActiveCardPacks().add(deepCopyPack(p.getDealtCardPacks().get(0)));
                    }
                    return players;
                })
                .map(players -> new ClassicGameInfo(
                        gameID,
                        creationInfo.cubeID(),
                        "classic",
                        players,
                        GameState.GAME_STARTED,
                        Instant.now(),
                        0,
                        DraftDirection.ASCENDING))
                .call(gameInfo -> dbHandler.addGame(gameInfo));
    }

    public Uni<Card> draftCard(final String playerToken,
                               final String cardID,
                               final String gameID) {
        LOGGER.info("Fetching Classic Game with ID: {}", gameID);
        return dbHandler.findGame(gameID)
                .chain(gameInfo -> {
                    if (gameInfo.getGameState() != GameState.GAME_STARTED) {
                        throw new IllegalStateException("Game " + gameID + " is not in progress. Current state: "
                                + gameInfo.getGameState());
                    }

                    ClassicPlayer player = gameInfo.getPlayers().stream()
                            .filter(p -> playerToken.equals(p.getPlayerToken()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Player not found"));

                    if (player.getActiveCardPacks().isEmpty()) {
                        throw new IllegalStateException("Player has no active pack to draft from");
                    }

                    CardPack currentPack = player.getActiveCardPacks().remove(0);
                    Card draftedCard = currentPack.removeCardFromPack(cardID);
                    player.getCardsDrafted().add(draftedCard);

                    if (!currentPack.getCardsInPack().isEmpty()) {
                        int playerIndex = gameInfo.getPlayers().indexOf(player);
                        int numPlayers = gameInfo.getPlayers().size();
                        int nextIndex;
                        if (gameInfo.getDraftDirection() == DraftDirection.ASCENDING) {
                            nextIndex = (playerIndex + 1) % numPlayers;
                        } else {
                            nextIndex = (playerIndex - 1 + numPlayers) % numPlayers;
                        }
                        gameInfo.getPlayers().get(nextIndex).getActiveCardPacks().add(currentPack);
                    }

                    LOGGER.info("Successfully drafted Card: {} for Player: {}",
                            draftedCard.getName(), player.getPlayerName());

                    return dbHandler.updateGame(gameInfo)
                            .chain(ignored -> advanceGenerationIfNeeded(gameInfo))
                            .replaceWith(draftedCard);
                });
    }

    private int findPlayerIndex(final List<ClassicPlayer> players, String playerName) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getPlayerName().equals(playerName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Player not found: " + playerName);
    }

    /**
     * Advances the draft generation once every active pack has been exhausted.
     * Each step mutates the in-memory game, persists it, and then re-evaluates
     * the condition — preserving the sequential semantics of the previous
     * synchronous implementation.
     */
    private Uni<Void> advanceGenerationIfNeeded(final ClassicGameInfo gameInfo) {
        int packsPerPlayer = gameInfo.getPlayers().get(0).getDealtCardPacks().size();

        if (gameInfo.getCurrentPackIndex() >= packsPerPlayer || !areAllPacksExhausted(gameInfo)) {
            return Uni.createFrom().voidItem();
        }

        int nextIndex = gameInfo.getCurrentPackIndex() + 1;
        if (nextIndex >= packsPerPlayer) {
            LOGGER.info("All packs exhausted. Completing game: {}", gameInfo.getGameID());
            gameInfo.setGameState(GameState.GAME_COMPLETE);
            gameInfo.setCurrentPackIndex(nextIndex);
            return dbHandler.updateGameState(gameInfo.getGameID(), GameState.GAME_COMPLETE)
                    .replaceWithVoid();
        }

        LOGGER.info("Pack index {} exhausted. Advancing to pack index {} and flipping direction.",
                gameInfo.getCurrentPackIndex(), nextIndex);
        gameInfo.setCurrentPackIndex(nextIndex);
        gameInfo.setDraftDirection(
                gameInfo.getDraftDirection() == DraftDirection.ASCENDING
                        ? DraftDirection.DESCENDING
                        : DraftDirection.ASCENDING
        );

        for (ClassicPlayer p : gameInfo.getPlayers()) {
            p.getActiveCardPacks().add(deepCopyPack(p.getDealtCardPacks().get(nextIndex)));
        }

        return dbHandler.updateGame(gameInfo)
                .chain(ignored -> advanceGenerationIfNeeded(gameInfo));
    }

    private CardPack deepCopyPack(final CardPack original) {
        return new CardPack(
                original.getPackNumber(),
                new ArrayList<>(original.getCardsInPack()),
                original.getOriginalCardsInPack(),
                original.getDoubleDraftedFlag());
    }

    private boolean areAllPacksExhausted(final ClassicGameInfo gameInfo) {
        return gameInfo.getPlayers().stream()
                .allMatch(p -> p.getActiveCardPacks().isEmpty());
    }

    public Uni<ClassicGameInfo> getGameInfo(final String gameID) {
        return dbHandler.findGame(gameID)
                .chain(game -> {
                    if (game.getGameState() != GameState.GAME_COMPLETE) {
                        throw new WebApplicationException(Response.Status.CONFLICT);
                    }
                    return Uni.createFrom().item(game);
                });
    }

    public Uni<GameStatusMessage> endGame(final String gameID) {
        return dbHandler.findGame(gameID)
                .chain(gameInfo -> {
                    if (gameInfo.getGameState() == GameState.GAME_COMPLETE) {
                        return Uni.createFrom().item(new GameStatusMessage(gameID, GameState.GAME_COMPLETE));
                    }
                    return dbHandler.updateGameState(gameID, GameState.GAME_COMPLETE)
                            .map(state -> new GameStatusMessage(gameID, state));
                });
    }

    public Uni<List<ClassicGameSummary>> getGameHistory(final String playerName) {
        return dbHandler.findGamesByPlayerName(playerName)
                .map(games -> games.stream()
                        .flatMap(game -> game.getPlayers().stream()
                                .filter(p -> p.getPlayerName().equals(playerName))
                                .map(p -> new ClassicGameSummary(
                                        game.getGameID(),
                                        game.getCubeID(),
                                        game.getGameState(),
                                        p.getPlayerName(),
                                        p.getDraftOrderNumber(),
                                        p.getCardsDrafted().size(),
                                        p.getDealtCardPacks().size(),
                                        game.getCurrentPackIndex(),
                                        game.getCreatedAt())))
                        .toList());
    }

    public Uni<PlayerDraftCheck> getDraftCheck(final String gameID, final String playerToken) {
        return dbHandler.findDraftCheck(gameID, playerToken)
                .chain(doc -> {
                    if (doc == null) {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                    return Uni.createFrom().item(toDraftCheck(doc));
                });
    }

    private static PlayerDraftCheck toDraftCheck(final Document doc) {
        String stateStr = doc.getString("gameState");
        GameState state = GameState.valueOf(stateStr);
        boolean canDraft = doc.getBoolean("canDraft", false);
        return new PlayerDraftCheck(canDraft, state);
    }

    public Uni<PlayerDraftData> getDraftData(final String gameID, final String playerToken) {
        return dbHandler.findGame(gameID)
                .map(game -> {
                    ClassicPlayer player = game.getPlayers().stream()
                            .filter(p -> playerToken.equals(p.getPlayerToken()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Player not found"));

                    List<CardPack> dealtCardPacks = player.getDealtCardPacks();
                    int cardsLeftToDraft = dealtCardPacks.size()
                            * (dealtCardPacks.isEmpty() ? 0 : dealtCardPacks.get(0).getOriginalCardsInPack())
                            - player.getCardsDrafted().size();

                    DraftPlayerSnapshot snapshot = new DraftPlayerSnapshot(
                            player.getPlayerName(),
                            player.getActiveCardPacks(),
                            player.getCardsDrafted(),
                            cardsLeftToDraft);

                    return new PlayerDraftData(gameID, game.getGameState(), game.getDraftDirection(), snapshot);
                });
    }
}
