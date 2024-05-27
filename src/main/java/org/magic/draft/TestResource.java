package org.magic.draft;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.magic.draft.api.GameInfo;
import org.magic.draft.api.card.Cube;
import org.magic.draft.app.GameCoordination.DbHandler;
import org.magic.draft.util.JsonUtility;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/test")
public class TestResource {
    private static final Logger LOGGER = LogManager.getLogger(TestResource.class);

    @Inject
    DbHandler dbHandler;

    @POST
    @Path("/db")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response testOutDB(final GameInfo gameInfo) {

        LOGGER.info("Testing DB Method");

        try {
            dbHandler.addGame(gameInfo);
        }
        catch(Exception e) {
            LOGGER.error(e.getMessage());
            return Response.serverError().build();
        }
        
        return Response.ok().build();
    }

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
