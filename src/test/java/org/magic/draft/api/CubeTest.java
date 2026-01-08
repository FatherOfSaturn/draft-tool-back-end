package org.magic.draft.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.magic.draft.api.card.Card;
import org.magic.draft.api.card.CardDetails;
import org.magic.draft.api.card.CardsInCube;
import org.magic.draft.api.card.Cube;
import org.magic.draft.util.JsonUtility;


public class CubeTest {

    @SuppressWarnings("unused")
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
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("JoshCube.json");
        String fileString = IOUtils.toString(inputStream, "UTF-8");

        Cube myCube = JsonUtility.getInstance().fromJson(fileString, Cube.class);
        System.out.println(JsonUtility.getInstance().toJson(myCube));
    }

    @Test
    void testRealCube() throws IOException {
        InputStream cubeIS = getClass().getClassLoader().getResourceAsStream("JoshCube.json");
        String cubeString = IOUtils.toString(cubeIS, "UTF-8");

        Cube cube = JsonUtility.getInstance().fromJson(cubeString, Cube.class);

        System.out.println(JsonUtility.getInstance().toJson(cube));
    }
    
    @Test
    void testGameInfo() throws IOException {

        InputStream gameIS = getClass().getClassLoader().getResourceAsStream("GameCreationInfo.json");
        String gameString = IOUtils.toString(gameIS, "UTF-8");

        GameCreationInfo info = JsonUtility.getInstance().fromJson(gameString, GameCreationInfo.class);

        System.out.println(JsonUtility.getInstance().toJson(info));
    }

    @Test
    void newFileRead() throws IOException {

        Path path = Paths.get("src/test/resources", "AdamCube.json");
        String cubeString = Files.readString(path);

        Cube cube = JsonUtility.getInstance().fromJson(cubeString, Cube.class);

        System.out.println("Cards in mainboard " + cube.getCards().getMainboard().size());
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
            System.out.println(e);
        }
    }

    // TODO:: Find way to make this static
    public Cube createCubeFromJson(final String fileName) throws IOException {

        InputStream cubeIS = getClass().getClassLoader().getResourceAsStream(fileName);
        String cubeString = IOUtils.toString(cubeIS, "UTF-8");

        Cube cube = JsonUtility.getInstance().fromJson(cubeString, Cube.class);

        return cube;
    }
}
