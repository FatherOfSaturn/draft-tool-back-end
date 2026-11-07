package org.magic.burnDraft.app.GameCoordination;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.magic.burnDraft.api.BurnGameCreationInfo;
import org.magic.burnDraft.api.BurnGameInfo;
import org.magic.burnDraft.api.BurnGameSummary;
import org.magic.burnDraft.api.BurnPlayer;
import org.magic.burnDraft.api.BurnPlayerSnapshot;
import org.magic.burnDraft.api.BurnRemainderPolicy;
import org.magic.burnDraft.api.BurnTurnRequest;
import org.magic.burnDraft.api.PlayerBurnCheck;
import org.magic.burnDraft.api.PlayerBurnData;
import org.magic.burnDraft.app.BurnPackCreator;
import org.magic.common.card.Card;
import org.magic.common.card.CardPack;
import org.magic.common.cube.CubeDownloader;
import org.magic.common.game.DraftDirection;
import org.magic.common.game.GameState;
import org.magic.common.game.GameStatusMessage;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Reactive coordinator for burn draft games.
 *
 * <p>A burn draft is a rotating (rotisserie) draft in which each turn consists
 * of drafting several cards and burning (discarding) several cards from the
 * pack before passing it on. Passing, generation advancement, direction
 * flipping and completion mirror the classic draft; the additional knobs are
 * the per-turn draft/burn counts and how a non-divisible final pass is handled
 * ({@link BurnRemainderPolicy}).</p>
 */
@ApplicationScoped
public class BurnGameCoordinationWorker {
    private static final Logger LOGGER = LogManager.getLogger(BurnGameCoordinationWorker.class);

    private final BurnDraftDbHandler dbHandler;
    private final CubeDownloader cubeDownloader;

    @Inject
    public BurnGameCoordinationWorker(final BurnDraftDbHandler dbHandler,
                                       final CubeDownloader cubeDownloader) {
        this.dbHandler = dbHandler;
        this.cubeDownloader = cubeDownloader;
    }

    public Uni<BurnGameInfo> startGame(final BurnGameCreationInfo creationInfo) {
        String gameID = UUID.randomUUID().toString();
        return cubeDownloader.getCubeForCubeID(creationInfo.cubeID())
                .map(cube -> new BurnPackCreator(cube, creationInfo.cardsPerPack(), creationInfo.packsPerPlayer()))
                .map(packCreator -> packCreator.createBurnPacks(creationInfo.players()))
                .map(players -> {
                    for (BurnPlayer p : players) {
                        if (p.getPlayerToken() == null || p.getPlayerToken().isBlank()) {
                            p.setPlayerToken(UUID.randomUUID().toString());
                        }
                    }
                    return players;
                })
                .map(players -> {
                    for (BurnPlayer p : players) {
                        p.getActiveCardPacks().add(deepCopyPack(p.getDealtCardPacks().get(0)));
                    }
                    return players;
                })
                .map(players -> new BurnGameInfo(
                        gameID,
                        creationInfo.cubeID(),
                        "burn",
                        players,
                        GameState.GAME_STARTED,
                        Instant.now(),
                        0,
                        DraftDirection.ASCENDING,
                        creationInfo.cardsDraftedPerPack(),
                        creationInfo.cardsBurnedPerPack(),
                        creationInfo.remainderPolicy()))
                .call(gameInfo -> dbHandler.addGame(gameInfo))
                .onFailure(IllegalArgumentException.class)
                .transform(t -> new BadRequestException(t.getMessage()));
    }

    /**
     * Executes one turn for the identified player: drafts the submitted cards and
     * burns the submitted cards from the pack they currently hold, then passes the
     * remainder on. The submission is validated against the game's turn rules,
     * including the remainder policy on a partial pass.
     *
     * @return a {@link Uni} emitting the cards the player drafted this turn
     */
    public Uni<List<Card>> takeTurn(final String playerToken,
                                    final String gameID,
                                    final BurnTurnRequest request) {
        LOGGER.info("Burn turn for game {} by token {}", gameID, playerToken == null ? "null" : "***");
        return dbHandler.findGame(gameID)
                .chain(gameInfo -> {
                    if (gameInfo.getGameState() != GameState.GAME_STARTED) {
                        throw new IllegalStateException("Game " + gameID + " is not in progress. Current state: "
                                + gameInfo.getGameState());
                    }

                    BurnPlayer player = gameInfo.getPlayers().stream()
                            .filter(p -> playerToken != null && playerToken.equals(p.getPlayerToken()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Player not found"));

                    if (player.getActiveCardPacks().isEmpty()) {
                        throw new IllegalStateException("Player has no active pack to draft from");
                    }

                    CardPack currentPack = player.getActiveCardPacks().get(0);
                    Map<String, Card> cardsInPack = currentPack.getCardsInPack().stream()
                            .collect(Collectors.toMap(Card::getCardID, card -> card));

                    validateTurn(gameInfo, currentPack, cardsInPack, request);
                    player.getActiveCardPacks().remove(0);

                    List<Card> draftedCards = new ArrayList<>();
                    for (String cardID : request.draftCardIds()) {
                        draftedCards.add(currentPack.removeCardFromPack(cardID));
                    }
                    List<Card> burnedCards = new ArrayList<>();
                    for (String cardID : request.burnCardIds()) {
                        burnedCards.add(currentPack.removeCardFromPack(cardID));
                    }

                    player.getCardsDrafted().addAll(draftedCards);
                    player.getCardsBurned().addAll(burnedCards);

                    if (!currentPack.getCardsInPack().isEmpty()) {
                        int playerIndex = gameInfo.getPlayers().indexOf(player);
                        int numPlayers = gameInfo.getPlayers().size();
                        int nextIndex = gameInfo.getDraftDirection() == DraftDirection.ASCENDING
                                ? (playerIndex + 1) % numPlayers
                                : (playerIndex - 1 + numPlayers) % numPlayers;
                        gameInfo.getPlayers().get(nextIndex).getActiveCardPacks().add(currentPack);
                    }

                    LOGGER.info("Player {} drafted {} and burned {} card(s) in game {}",
                            player.getPlayerName(), draftedCards.size(), burnedCards.size(), gameID);

                    return dbHandler.updateGame(gameInfo)
                            .chain(ignored -> advanceGenerationIfNeeded(gameInfo))
                            .replaceWith(draftedCards);
                });
    }

    private static void validateTurn(final BurnGameInfo gameInfo,
                                     final CardPack currentPack,
                                     final Map<String, Card> cardsInPack,
                                     final BurnTurnRequest request) {
        Set<String> draftIds = new HashSet<>(request.draftCardIds());
        Set<String> burnIds = new HashSet<>(request.burnCardIds());

        if (draftIds.size() != request.draftCardIds().size()) {
            throw new BadRequestException("draftCardIds contains duplicate cards");
        }
        if (burnIds.size() != request.burnCardIds().size()) {
            throw new BadRequestException("burnCardIds contains duplicate cards");
        }
        if (!java.util.Collections.disjoint(draftIds, burnIds)) {
            throw new BadRequestException("A card cannot be both drafted and burned in the same turn");
        }
        for (String cardID : draftIds) {
            if (!cardsInPack.containsKey(cardID)) {
                throw new BadRequestException("Card " + cardID + " is not in the pack you currently hold");
            }
        }
        for (String cardID : burnIds) {
            if (!cardsInPack.containsKey(cardID)) {
                throw new BadRequestException("Card " + cardID + " is not in the pack you currently hold");
            }
        }

        TurnCounts expected = expectedTurnCounts(
                currentPack.getCardsInPack().size(),
                gameInfo.getCardsDraftedPerPack(),
                gameInfo.getCardsBurnedPerPack(),
                gameInfo.getRemainderPolicy());
        if (request.draftCardIds().size() != expected.drafted()
                || request.burnCardIds().size() != expected.burned()) {
            throw new BadRequestException("Invalid turn: expected to draft " + expected.drafted()
                    + " and burn " + expected.burned() + " card(s) from a pack of "
                    + currentPack.getCardsInPack().size() + " under " + gameInfo.getRemainderPolicy()
                    + ", got draft=" + request.draftCardIds().size()
                    + " burn=" + request.burnCardIds().size());
        }
    }

    /**
     * Computes how many cards a turn must draft and burn for the given pack size.
     *
     * <ul>
     *   <li>full pass ({@code remaining >= draftQuota + burnQuota}): draft all of the
     *       draft quota and burn all of the burn quota;</li>
     *   <li>partial pass: draft as much of the draft quota as possible; the leftover
     *       cards are burned under {@link BurnRemainderPolicy#BURN_EXTRAS} or drafted
     *       under {@link BurnRemainderPolicy#DRAFT_EXTRAS}.</li>
     * </ul>
     */
    static TurnCounts expectedTurnCounts(final int remaining, final int draftQuota,
                                         final int burnQuota, final BurnRemainderPolicy policy) {
        if (remaining >= draftQuota + burnQuota) {
            return new TurnCounts(draftQuota, burnQuota);
        }
        int drafted = Math.min(draftQuota, remaining);
        int extras = remaining - drafted;
        if (policy == BurnRemainderPolicy.DRAFT_EXTRAS) {
            return new TurnCounts(drafted + extras, 0);
        }
        return new TurnCounts(drafted, extras);
    }

    /** The number of cards a single turn drafts and burns. */
    record TurnCounts(int drafted, int burned) {
    }

    private Uni<Void> advanceGenerationIfNeeded(final BurnGameInfo gameInfo) {
        int packsPerPlayer = gameInfo.getPlayers().get(0).getDealtCardPacks().size();

        if (gameInfo.getCurrentPackIndex() >= packsPerPlayer || !areAllPacksExhausted(gameInfo)) {
            return Uni.createFrom().voidItem();
        }

        int nextIndex = gameInfo.getCurrentPackIndex() + 1;
        if (nextIndex >= packsPerPlayer) {
            LOGGER.info("All packs exhausted. Completing burn game: {}", gameInfo.getGameID());
            gameInfo.setGameState(GameState.GAME_COMPLETE);
            gameInfo.setCurrentPackIndex(nextIndex);
            return dbHandler.updateGameState(gameInfo.getGameID(), GameState.GAME_COMPLETE)
                    .replaceWithVoid();
        }

        LOGGER.info("Pack index {} exhausted. Advancing burn game {} to pack index {} and flipping direction.",
                gameInfo.getCurrentPackIndex(), gameInfo.getGameID(), nextIndex);
        gameInfo.setCurrentPackIndex(nextIndex);
        gameInfo.setDraftDirection(
                gameInfo.getDraftDirection() == DraftDirection.ASCENDING
                        ? DraftDirection.DESCENDING
                        : DraftDirection.ASCENDING);

        for (BurnPlayer p : gameInfo.getPlayers()) {
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

    private boolean areAllPacksExhausted(final BurnGameInfo gameInfo) {
        return gameInfo.getPlayers().stream()
                .allMatch(p -> p.getActiveCardPacks().isEmpty());
    }

    public Uni<BurnGameInfo> getGameInfo(final String gameID) {
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

    public Uni<List<BurnGameSummary>> getGameHistory(final String playerName) {
        return dbHandler.findGamesByPlayerName(playerName)
                .map(games -> games.stream()
                        .flatMap(game -> game.getPlayers().stream()
                                .filter(p -> p.getPlayerName().equals(playerName))
                                .map(p -> new BurnGameSummary(
                                        game.getGameID(),
                                        game.getCubeID(),
                                        game.getGameState(),
                                        p.getPlayerName(),
                                        p.getDraftOrderNumber(),
                                        p.getCardsDrafted().size(),
                                        p.getCardsBurned().size(),
                                        p.getDealtCardPacks().size(),
                                        game.getCurrentPackIndex(),
                                        game.getCreatedAt())))
                        .toList());
    }

    public Uni<PlayerBurnCheck> getDraftCheck(final String gameID, final String playerToken) {
        return dbHandler.findDraftCheck(gameID, playerToken)
                .chain(doc -> {
                    if (doc == null) {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                    return Uni.createFrom().item(toDraftCheck(doc));
                });
    }

    private static PlayerBurnCheck toDraftCheck(final Document doc) {
        String stateStr = doc.getString("gameState");
        GameState state = GameState.valueOf(stateStr);
        boolean canDraft = doc.getBoolean("canDraft", false);
        return new PlayerBurnCheck(canDraft, state);
    }

    public Uni<PlayerBurnData> getDraftData(final String gameID, final String playerToken) {
        return dbHandler.findGame(gameID)
                .map(game -> {
                    BurnPlayer player = game.getPlayers().stream()
                            .filter(p -> playerToken != null && playerToken.equals(p.getPlayerToken()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Player not found"));

                    List<CardPack> dealtCardPacks = player.getDealtCardPacks();
                    int cardsLeftToDraft = dealtCardPacks.size()
                            * (dealtCardPacks.isEmpty() ? 0 : dealtCardPacks.get(0).getOriginalCardsInPack())
                            - player.getCardsDrafted().size()
                            - player.getCardsBurned().size();

                    BurnPlayerSnapshot snapshot = new BurnPlayerSnapshot(
                            player.getPlayerName(),
                            player.getActiveCardPacks(),
                            player.getCardsDrafted(),
                            player.getCardsBurned(),
                            cardsLeftToDraft);

                    return new PlayerBurnData(
                            gameID,
                            game.getGameState(),
                            game.getDraftDirection(),
                            game.getCardsDraftedPerPack(),
                            game.getCardsBurnedPerPack(),
                            game.getRemainderPolicy(),
                            snapshot);
                });
    }
}
