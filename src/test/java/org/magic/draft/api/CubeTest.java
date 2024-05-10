package org.magic.draft.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardDetails;
import org.magic.draft.api.card.CardsInCube;
import org.magic.draft.api.card.Cube;
import org.magic.draft.util.JsonUtility;


public class CubeTest {

    @Test
    void testCubeSerialization() throws IOException {

        InputStream detailIs = getClass().getClassLoader().getResourceAsStream("CardDetails.json");
        String detailsString = IOUtils.toString(detailIs, "UTF-8");

        CardDetails details = JsonUtility.getInstance().fromJson(detailsString, CardDetails.class);
        String json = JsonUtility.getInstance().toJson(details);
        System.out.println(json);

        CardDetails myCardDeets = JsonUtility.getInstance().fromJson(detailsString, CardDetails.class);
        System.out.println(JsonUtility.getInstance().toJson(myCardDeets));

        InputStream cardStream = getClass().getClassLoader().getResourceAsStream("Card.json");
        String cardString = IOUtils.toString(cardStream, "UTF-8");

        Card myCard = JsonUtility.getInstance().fromJson(cardString, Card.class);
        System.out.println(JsonUtility.getInstance().toJson(myCard));

        InputStream cicStream = getClass().getClassLoader().getResourceAsStream("CardsInCube.json");
        String cicString = IOUtils.toString(cicStream, "UTF-8");
        CardsInCube cardsInCube = JsonUtility.getInstance().fromJson(cicString, CardsInCube.class);
        System.out.println(JsonUtility.getInstance().toJson(cardsInCube));

        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("TestCube.json");
        String fileString = IOUtils.toString(inputStream, "UTF-8");

        Cube myCube = JsonUtility.getInstance().fromJson(fileString, Cube.class);
        System.out.println(JsonUtility.getInstance().toJson(myCube));
    }

    @Test
    void testCardsInCube() throws IOException {

        InputStream cicStream = getClass().getClassLoader().getResourceAsStream("CardsInCube.json");
        String cicString = IOUtils.toString(cicStream, "UTF-8");

        System.out.println(cicString);

        // CardsInCube cardsInCube = JsonUtility.getInstance().fromJson(cicString, CardsInCube.class);
        // System.out.println(JsonbUtility.jsonb().toJson(cardsInCube));
    }

    @Test
    void testRealCube() throws IOException {
        InputStream cubeIS = getClass().getClassLoader().getResourceAsStream("FullCube.json");
        String cubeString = IOUtils.toString(cubeIS, "UTF-8");

        Cube cube = JsonUtility.getInstance().fromJson(cubeString, Cube.class);

        System.out.println(JsonUtility.getInstance().toJson(cube));
    }
    
    @Test
    void testGameInfo() throws IOException {

        InputStream gameIS = getClass().getClassLoader().getResourceAsStream("GameInfo.json");
        String gameString = IOUtils.toString(gameIS, "UTF-8");

        GameInfo info = JsonUtility.getInstance().fromJson(gameString, GameInfo.class);

        System.out.println(JsonUtility.getInstance().toJson(info));
    }

    @Test
    void dumbURL() {
        String urlString = "https://cubecobra.com/";
        try {
            URL url = new URL(urlString);
            // URL is valid
            System.out.println("URL is valid: " + url);
        } catch (MalformedURLException e) {
            // URL is invalid
            System.out.println("URL is invalid: " + urlString);
            e.printStackTrace();
        }
    }
}
