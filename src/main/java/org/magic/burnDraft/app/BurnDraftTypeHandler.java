package org.magic.burnDraft.app;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.magic.burnDraft.api.BurnGameCreationInfo;
import org.magic.burnDraft.api.BurnPlayerCreationInfo;
import org.magic.burnDraft.api.BurnRemainderPolicy;
import org.magic.burnDraft.app.GameCoordination.BurnGameCoordinationWorker;
import org.magic.lobbyService.api.LobbyPlayer;
import org.magic.lobbyService.app.DraftTypeHandler;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * {@link DraftTypeHandler} that boots a burn draft game from a lobby.
 *
 * <p>Lobby config keys: {@code cubeID} (required), {@code packsPerPlayer},
 * {@code cardsPerPack}, {@code cardsDraftedPerPack}, {@code cardsBurnedPerPack}
 * and {@code remainderPolicy} ({@code burn_extras} or {@code draft_extras}).
 * The number of packs is derived from the live player count.</p>
 */
@ApplicationScoped
public class BurnDraftTypeHandler implements DraftTypeHandler {

    private final BurnGameCoordinationWorker gameWorker;

    @Inject
    public BurnDraftTypeHandler(final BurnGameCoordinationWorker gameWorker) {
        this.gameWorker = gameWorker;
    }

    @Override
    public String draftType() {
        return "burn";
    }

    @Override
    public int defaultMinPlayers() {
        return 2;
    }

    @Override
    public int defaultMaxPlayers() {
        return 12;
    }

    @Override
    public Uni<String> startGame(final String cubeID,
                                 final List<LobbyPlayer> players,
                                 final Map<String, Object> config) {
        int cardsPerPack = DraftTypeHandler.intConfig(config, "cardsPerPack", 15);
        int packsPerPlayer = DraftTypeHandler.intConfig(config, "packsPerPlayer", 3);
        int cardsDraftedPerPack = DraftTypeHandler.intConfig(config, "cardsDraftedPerPack", 1);
        int cardsBurnedPerPack = DraftTypeHandler.intConfig(config, "cardsBurnedPerPack", 1);
        BurnRemainderPolicy remainderPolicy = remainderPolicy(config);

        List<BurnPlayerCreationInfo> playerInfo = players.stream()
                .sorted(Comparator.comparingInt(LobbyPlayer::getSlotIndex))
                .map(p -> new BurnPlayerCreationInfo(p.getDisplayName(), p.getAccountID(), p.getPlayerToken()))
                .toList();

        int numberOfPacks = playerInfo.size() * packsPerPlayer;

        BurnGameCreationInfo creationInfo = new BurnGameCreationInfo(
                cubeID,
                playerInfo,
                numberOfPacks,
                cardsPerPack,
                packsPerPlayer,
                cardsDraftedPerPack,
                cardsBurnedPerPack,
                remainderPolicy);

        return gameWorker.startGame(creationInfo)
                .map(game -> game.getGameID());
    }

    private static BurnRemainderPolicy remainderPolicy(final Map<String, Object> config) {
        Object raw = config.get("remainderPolicy");
        if (raw == null || (raw instanceof String text && text.isBlank())) {
            return BurnRemainderPolicy.BURN_EXTRAS;
        }
        try {
            return BurnRemainderPolicy.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid remainderPolicy in lobby config: " + raw);
        }
    }
}
