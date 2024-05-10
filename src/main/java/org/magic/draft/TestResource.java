package org.magic.draft;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.magic.draft.api.card.Cube;
import org.magic.draft.util.JsonUtility;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/test")
public class TestResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {

        try {
            // Define the URL
            URL url = new URL("https://cubecobra.com/cube/api/cubeJSON/e77c5054-861d-4689-af1a-732736ef789b");

            // Open a connection to the URL
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Set the request method
            conn.setRequestMethod("GET");

            // Read the response
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Print the response
            System.out.println("Response from server:");

            JsonUtility.getInstance().fromJson(response.toString(), Cube.class);

            System.out.println(response.toString());

            // Close the connection
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Hello from Quarkus REST";
    }
}
