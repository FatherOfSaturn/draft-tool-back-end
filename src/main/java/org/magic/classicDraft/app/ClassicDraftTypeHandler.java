package org.magic.classicDraft.app;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.magic.classicDraft.api.ClassicGameCreationInfo;
import org.magic.classicDraft.api.ClassicPlayerCreationInfo;
import org.magic.classicDraft.app.GameCoordination.ClassicGameCoordinationWorker;
import org.magic.lobbyService.api.LobbyPlayer;
import org.magic.lobbyService.app.DraftTypeHandler;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * {@link DraftTypeHandler} that boots a classic draft game from a lobby.
 * The number of packs is derived from the live player count, so players joining
 * after lobby creation cannot break game start.
 */
@ApplicationScoped
public class ClassicDraftTypeHandler implements DraftTypeHandler {

    private final ClassicGameCoordinationWorker gameWorker;

    @Inject
    public ClassicDraftTypeHandler(final ClassicGameCoordinationWorker gameWorker) {
        this.gameWorker = gameWorker;
    }

    @Override
    public String draftType() {
        return "classic";
    }

    @Override
    public int defaultMinPlayers() {
        return 4;
    }

    @Override
    public int defaultMaxPlayers() {
        return 12;
    }

    @Override
    public Uni<String> startGame(final String cubeID,
                                 final List<LobbyPlayer> players,
                                 final Map<String, Object> config) {
        int cardsPerPack = intConfig(config, "cardsPerPack", 15);
        int packsPerPlayer = intConfig(config, "packsPerPlayer", 3);

        List<ClassicPlayerCreationInfo> playerInfo = players.stream()
                .sorted(Comparator.comparingInt(LobbyPlayer::getSlotIndex))
                .map(p -> new ClassicPlayerCreationInfo(p.getDisplayName(), p.getAccountID()))
                .toList();

        int numberOfPacks = playerInfo.size() * packsPerPlayer;

        ClassicGameCreationInfo creationInfo = new ClassicGameCreationInfo(
                cubeID, playerInfo, numberOfPacks, cardsPerPack, packsPerPlayer);

        return gameWorker.startGame(creationInfo)
                .map(game -> game.getGameID());
    }

    private static int intConfig(final Map<String, Object> config, final String key, final int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }
}
