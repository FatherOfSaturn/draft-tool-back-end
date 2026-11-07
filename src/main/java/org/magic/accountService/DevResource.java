package org.magic.accountService;

import java.util.Date;
import java.util.Map;

import io.jsonwebtoken.Jwts;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Development-only REST resource for generating mock JWT tokens.
 * Used to bypass Google OAuth during local development. The bean is only
 * registered in the {@code dev} profile, so the endpoint is not present
 * in production builds.
 */
@Path("/dev")
@Produces(MediaType.APPLICATION_JSON)
@IfBuildProfile("dev")
@Tag(name = "Dev", description = "Development-only helpers (available only in the dev profile).")
public class DevResource {

    /**
     * Generates a mock JWT token for local development. All parameters are optional
     * and default to sensible dev values. The token is valid for 24 hours.
     *
     * @param sub   the subject claim (default: "dev-user-123")
     * @param email the email claim (default: "dev@example.com")
     * @param name  the name claim (default: "Dev User")
     * @return a JSON object containing the {@code idToken} and the claims used
     */
    @GET
    @Path("/token")
    @Operation(summary = "Generate a dev token",
            description = "Generates a mock Google ID token for local development, bypassing OAuth. " +
                    "Only available in the dev profile.")
    public Response generateDevToken(@QueryParam("sub") final String sub,
                                     @QueryParam("email") final String email,
                                     @QueryParam("name") final String name) {
        String subject = sub != null ? sub : "dev-user-123";
        String userEmail = email != null ? email : "dev@example.com";
        String userName = name != null ? name : "Dev User";

        String token = Jwts.builder()
                .subject(subject)
                .claim("email", userEmail)
                .claim("email_verified", true)
                .claim("name", userName)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86_400_000))
                .compact();

        return Response.ok(Map.of(
                "idToken", token,
                "sub", subject,
                "email", userEmail,
                "name", userName
        )).build();
    }
}
