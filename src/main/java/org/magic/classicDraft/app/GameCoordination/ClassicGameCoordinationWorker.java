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
import org.magic.classicDraft.api.ClassicGameState;
import org.magic.classicDraft.api.ClassicGameSummary;
import org.magic.classicDraft.api.ClassicPlayer;
import org.magic.classicDraft.api.DraftDirection;
import org.magic.classicDraft.api.DraftPlayerSnapshot;
import org.magic.classicDraft.api.PlayerDraftCheck;
import org.magic.classicDraft.api.PlayerDraftData;
import org.magic.classicDraft.app.ClassicPackCreator;
import org.magic.pyramidDraft.api.GameStatusMessage;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.app.CubeDownloader;
import org.magic.pyramidDraft.api.card.CardPack;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

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
                        p.getActiveCardPacks().add(deepCopyPack(p.getDealtCardPacks().get(0)));
                    }
                    return players;
                })
                .map(players -> new ClassicGameInfo(
                        gameID,
                        creationInfo.cubeID(),
                        "classic",
                        players,
                        ClassicGameState.GAME_STARTED,
                        Instant.now(),
                        0,
                        DraftDirection.ASCENDING))
                .invoke(gameInfo -> dbHandler.addGame(gameInfo));
    }

    public Card draftCard(final String playerName,
                          final String cardID,
                          final String gameID) {
        LOGGER.info("Fetching Classic Game with ID: {}", gameID);
        ClassicGameInfo gameInfo = dbHandler.findGame(gameID);

        if (gameInfo.getGameState() != ClassicGameState.GAME_STARTED) {
            throw new IllegalStateException("Game " + gameID + " is not in progress. Current state: " + gameInfo.getGameState());
        }

        ClassicPlayer player = gameInfo.getPlayers().stream()
                .filter(p -> p.getPlayerName().equals(playerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerName));

        if (player.getActiveCardPacks().isEmpty()) {
            throw new IllegalStateException("Player " + playerName + " has no active pack to draft from");
        }

        CardPack currentPack = player.getActiveCardPacks().remove(0);
        Card draftedCard = currentPack.removeCardFromPack(cardID);
        player.getCardsDrafted().add(draftedCard);

        if (!currentPack.getCardsInPack().isEmpty()) {
            int playerIndex = findPlayerIndex(gameInfo.getPlayers(), playerName);
            int numPlayers = gameInfo.getPlayers().size();
            int nextIndex;
            if (gameInfo.getDraftDirection() == DraftDirection.ASCENDING) {
                nextIndex = (playerIndex + 1) % numPlayers;
            } else {
                nextIndex = (playerIndex - 1 + numPlayers) % numPlayers;
            }
            gameInfo.getPlayers().get(nextIndex).getActiveCardPacks().add(currentPack);
        }

        LOGGER.info("Successfully drafted Card: {} for Player: {}", draftedCard.getName(), playerName);

        dbHandler.updateGame(gameInfo);

        checkAndAdvanceGeneration(gameInfo);

        return draftedCard;
    }

    private int findPlayerIndex(final List<ClassicPlayer> players, String playerName) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getPlayerName().equals(playerName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Player not found: " + playerName);
    }

    private void checkAndAdvanceGeneration(final ClassicGameInfo gameInfo) {
        int packsPerPlayer = gameInfo.getPlayers().get(0).getDealtCardPacks().size();

        while (gameInfo.getCurrentPackIndex() < packsPerPlayer && areAllPacksExhausted(gameInfo)) {
            int nextIndex = gameInfo.getCurrentPackIndex() + 1;
            if (nextIndex >= packsPerPlayer) {
                LOGGER.info("All packs exhausted. Completing game: {}", gameInfo.getGameID());
                gameInfo.setGameState(ClassicGameState.GAME_COMPLETE);
                gameInfo.setCurrentPackIndex(nextIndex);
                dbHandler.updateGameState(gameInfo.getGameID(), ClassicGameState.GAME_COMPLETE);
                return;
            }

            LOGGER.info("Pack index {} exhausted. Advancing to pack index {} and flipping direction.", gameInfo.getCurrentPackIndex(), nextIndex);
            gameInfo.setCurrentPackIndex(nextIndex);
            gameInfo.setDraftDirection(
                gameInfo.getDraftDirection() == DraftDirection.ASCENDING
                    ? DraftDirection.DESCENDING
                    : DraftDirection.ASCENDING
            );

            for (ClassicPlayer p : gameInfo.getPlayers()) {
                p.getActiveCardPacks().add(deepCopyPack(p.getDealtCardPacks().get(nextIndex)));
            }

            dbHandler.updateGame(gameInfo);
        }
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
        ClassicGameInfo game = dbHandler.findGame(gameID);
        if (game.getGameState() != ClassicGameState.GAME_COMPLETE) {
            throw new WebApplicationException(Response.Status.CONFLICT);
        }
        return Uni.createFrom().item(game);
    }

    public Uni<GameStatusMessage> endGame(final String gameID) {
        ClassicGameInfo gameInfo = dbHandler.findGame(gameID);
        if (gameInfo.getGameState() != ClassicGameState.GAME_COMPLETE) {
            dbHandler.updateGameState(gameID, ClassicGameState.GAME_COMPLETE);
        }
        return Uni.createFrom().item(new GameStatusMessage(gameID,
                org.magic.pyramidDraft.api.GameState.GAME_COMPLETE));
    }

    public Uni<List<ClassicGameSummary>> getGameHistory(final String playerName) {
        List<ClassicGameInfo> games = dbHandler.findGamesByPlayerName(playerName);

        List<ClassicGameSummary> summaries = games.stream()
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
                .toList();

        return Uni.createFrom().item(summaries);
    }

    public Uni<PlayerDraftCheck> getDraftCheck(final String gameID, final String playerName) {
        Document doc = dbHandler.findDraftCheck(gameID, playerName);
        if (doc == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        String stateStr = doc.getString("gameState");
        ClassicGameState state = ClassicGameState.valueOf(stateStr);
        boolean canDraft = doc.getBoolean("canDraft", false);
        return Uni.createFrom().item(new PlayerDraftCheck(canDraft, state));
    }

    public Uni<PlayerDraftData> getDraftData(final String gameID, final String playerName) {
        ClassicGameInfo game = dbHandler.findGame(gameID);

        ClassicPlayer player = game.getPlayers().stream()
                .filter(p -> p.getPlayerName().equals(playerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerName));

        List<CardPack> dealtCardPacks = player.getDealtCardPacks();
        int cardsLeftToDraft = dealtCardPacks.size()
                * (dealtCardPacks.isEmpty() ? 0 : dealtCardPacks.get(0).getOriginalCardsInPack())
                - player.getCardsDrafted().size();

        DraftPlayerSnapshot snapshot = new DraftPlayerSnapshot(
                player.getPlayerName(),
                player.getActiveCardPacks(),
                player.getCardsDrafted(),
                cardsLeftToDraft);

        return Uni.createFrom().item(new PlayerDraftData(gameID, game.getGameState(), game.getDraftDirection(), snapshot));
    }
}
