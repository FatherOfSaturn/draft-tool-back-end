package org.magic.classicDraft.api;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClassicGameCreationInfo(
    @JsonProperty("cubeID") String cubeID,
    @JsonProperty("players") List<ClassicPlayerCreationInfo> players,
    @JsonProperty("numberOfPacks") int numberOfPacks,
    @JsonProperty("cardsPerPack") int cardsPerPack,
    @JsonProperty("packsPerPlayer") int packsPerPlayer
) {

    public ClassicGameCreationInfo {
        Objects.requireNonNull(cubeID, "cubeID Required for ClassicGameCreationInfo");
        Objects.requireNonNull(players, "players Required for ClassicGameCreationInfo");
        if (players.size() < 4 || players.size() > 12) {
            throw new IllegalArgumentException("Number of players must be between 4 and 12, got " + players.size());
        }
        if (cardsPerPack < players.size() || cardsPerPack > 25) {
            throw new IllegalArgumentException("cardsPerPack must be between " + players.size() + " and 25, got " + cardsPerPack);
        }
        if (numberOfPacks != players.size() * packsPerPlayer) {
            throw new IllegalArgumentException("numberOfPacks (" + numberOfPacks
                + ") must equal players.size() * packsPerPlayer (" + players.size() + " * " + packsPerPlayer + " = "
                + (players.size() * packsPerPlayer) + ")");
        }
    }
}
