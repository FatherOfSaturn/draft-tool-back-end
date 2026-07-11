package org.magic.pyramidDraft.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.magic.common.util.JsonUtility;
import org.magic.pyramidDraft.api.card.Card;
import org.magic.pyramidDraft.api.card.CardDetails;
import org.magic.pyramidDraft.api.card.CardsInCube;
import org.magic.pyramidDraft.api.card.Cube;

import static org.junit.jupiter.api.Assertions.*;

public class CubeTest {

    @Test
    void testCardDetailsSerialization() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("CardDetails.json");
        String json = IOUtils.toString(is, "UTF-8");

        CardDetails details = JsonUtility.getInstance().fromJson(json, CardDetails.class);

        assertEquals("isd", details.getSet());
        assertEquals("Innistrad", details.getSet_name());
        assertEquals("Delver of Secrets", details.getName());
        assertEquals("11bf83bb-c95b-4b4f-9a56-ce7a1816307a", details.getScryfall_id());
        assertEquals("https://cards.scryfall.io/small/front/1/1/11bf83bb-c95b-4b4f-9a56-ce7a1816307a.jpg?1562826346", details.getImage_small());
        assertEquals("https://cards.scryfall.io/normal/front/1/1/11bf83bb-c95b-4b4f-9a56-ce7a1816307a.jpg?1562826346", details.getImage_normal());
        assertEquals("https://cards.scryfall.io/normal/back/1/1/11bf83bb-c95b-4b4f-9a56-ce7a1816307a.jpg?1562826346", details.getImageFlip());
        assertEquals("Creature \u2014 Human Wizard", details.getType());
        assertEquals(1, details.getCmc());
        assertEquals(List.of("u"), details.getParsed_cost());
    }

    @Test
    void testCardSerialization() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("Card.json");
        String json = IOUtils.toString(is, "UTF-8");

        Card card = JsonUtility.getInstance().fromJson(json, Card.class);

        assertEquals("11bf83bb-c95b-4b4f-9a56-ce7a1816307a", card.getCardID());
        assertEquals("Delver of Secrets", card.getName());
        assertEquals(1, card.getCmc());
        assertEquals("Creature \u2014 Human Wizard", card.getType_line());
        assertNotNull(card.getCardDetails());
        assertEquals("Delver of Secrets", card.getCardDetails().getName());
    }

    @Test
    void testCardsInCubeSerialization() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("CardsInCube.json");
        String json = IOUtils.toString(is, "UTF-8");

        CardsInCube cardsInCube = JsonUtility.getInstance().fromJson(json, CardsInCube.class);

        assertNotNull(cardsInCube.getMainboard());
        assertEquals(3, cardsInCube.getMainboard().size());
        assertEquals("card-001", cardsInCube.getMainboard().get(0).getCardID());
        assertEquals("Test Card Alpha", cardsInCube.getMainboard().get(0).getName());
    }

    @Test
    void testCubeSerialization_JoshCube() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("JoshCube.json");
        String json = IOUtils.toString(is, "UTF-8");

        Cube cube = JsonUtility.getInstance().fromJson(json, Cube.class);

        assertEquals("Clone of Innistrad Theme", cube.getName());
        assertNotNull(cube.getCards());
        assertEquals(360, cube.getCards().getMainboard().size());
    }

    @Test
    void testCubeSerialization_AdamCube() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("AdamCube.json");
        String json = IOUtils.toString(is, "UTF-8");

        Cube cube = JsonUtility.getInstance().fromJson(json, Cube.class);

        assertEquals("legacy-schmegacy", cube.getName());
        assertNotNull(cube.getCards());
        assertEquals(540, cube.getCards().getMainboard().size());
    }

    @Test
    void testGameCreationInfoSerialization() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("GameCreationInfo.json");
        String json = IOUtils.toString(is, "UTF-8");

        GameCreationInfo info = JsonUtility.getInstance().fromJson(json, GameCreationInfo.class);

        assertEquals("gameID", info.gameID());
        assertEquals("f7314414-c2d2-48ed-af2c-764cf0207c62", info.cubeID());
        assertEquals(4, info.numberOfDoubleDraftPicksPerPlayer());
        assertNotNull(info.playerInfo());
        assertEquals(2, info.playerInfo().size());
        assertEquals("Josh", info.playerInfo().get(0).playerName());
        assertEquals("Zach", info.playerInfo().get(1).playerName());
        assertNull(info.accountID());
        assertNull(info.partnerAccountID());
        assertNull(info.accountName());
    }

    @Test
    void testGameInfoSerialization() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("Game.json");
        String json = IOUtils.toString(is, "UTF-8");

        GameInfo gameInfo = JsonUtility.getInstance().fromJson(json, GameInfo.class);

        assertEquals("gameID", gameInfo.getGameID());
        assertEquals(GameState.GAME_STARTED, gameInfo.getGameState());
        assertNotNull(gameInfo.getPlayers());
        assertEquals(2, gameInfo.getPlayers().size());
        assertNull(gameInfo.getAccountID());
        assertNull(gameInfo.getPartnerAccountID());
        assertNull(gameInfo.getAccountName());
    }

    @Test
    void testPlayerSerialization_Player1() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("Player1.json");
        String json = IOUtils.toString(is, "UTF-8");

        Player player = JsonUtility.getInstance().fromJson(json, Player.class);

        assertEquals("Josh", player.getPlayerName());
        assertNotNull(player.getCardPacks());
        assertTrue(player.getCardPacks().size() > 0);
    }

    @Test
    void testPlayerSerialization_Player2() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("Player2.json");
        String json = IOUtils.toString(is, "UTF-8");

        Player player = JsonUtility.getInstance().fromJson(json, Player.class);

        assertEquals("Zach", player.getPlayerName());
        assertNotNull(player.getCardPacks());
        assertTrue(player.getCardPacks().size() > 0);
    }
}
