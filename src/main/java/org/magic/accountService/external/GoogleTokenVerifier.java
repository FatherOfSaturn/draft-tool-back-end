package org.magic.accountService.external;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Verifies Google OAuth ID tokens using Google's JWKS (JSON Web Key Set) endpoint.
 * In production, performs full JWS signature verification by fetching the public key
 * corresponding to the token's {@code kid} header. In dev mode, skips signature
 * verification and directly parses the JWT payload.
 *
 * <p>JWKS are cached for 60 minutes to avoid repeated HTTP calls to Google.</p>
 */
@ApplicationScoped
public class GoogleTokenVerifier {

    private static final Logger LOGGER = LogManager.getLogger(GoogleTokenVerifier.class);

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private final String clientId;
    private final boolean devMode;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile Map<String, Object> cachedJwks;
    private volatile Instant jwksFetchedAt;

    @Inject
    public GoogleTokenVerifier(@ConfigProperty(name = "google.oauth.client-id", defaultValue = "") final String clientId,
                               @ConfigProperty(name = "google.oauth.dev-mode", defaultValue = "false") final boolean devMode) {
        this.clientId = clientId;
        this.devMode = devMode;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Verifies a Google ID token and extracts user information. In dev mode, delegates
     * to {@link #verifyDev(String)} which skips signature verification. In production,
     * fetches Google's JWKS, locates the key matching the token's {@code kid}, builds
     * the RSA public key, and validates the signature, issuer, and audience.
     *
     * @param idToken the Google OAuth ID token (JWT format)
     * @return a {@link GoogleUser} with the extracted claims, or {@code null} if verification fails
     */
    public GoogleUser verify(final String idToken) {
        try {
            if (devMode) {
                return verifyDev(idToken);
            }

            String kid = extractKid(idToken);
            if (kid == null) {
                LOGGER.warn("No kid in token header");
                return null;
            }

            Map<String, Object> jwk = findJwkByKid(kid);
            if (jwk == null) {
                LOGGER.warn("No JWK found for kid: {}", kid);
                return null;
            }

            PublicKey publicKey = buildPublicKey(jwk);
            if (publicKey == null) {
                LOGGER.warn("Failed to build public key from JWK");
                return null;
            }

            JwtParser parser = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(GOOGLE_ISSUER)
                    .requireAudience(clientId)
                    .clockSkewSeconds(30)
                    .build();

            Claims claims = parser.parseSignedClaims(idToken).getPayload();

            String email = claims.get("email", String.class);
            Boolean emailVerified = claims.get("email_verified", Boolean.class);
            String name = claims.get("name", String.class);
            String sub = claims.getSubject();

            if (email == null || !Boolean.TRUE.equals(emailVerified)) {
                LOGGER.warn("Email not verified for Google user: {}", sub);
                return null;
            }

            return new GoogleUser(sub, email, name != null ? name : email);

        } catch (JwtException e) {
            LOGGER.error("JWT verification failed", e);
            return null;
        } catch (Exception e) {
            LOGGER.error("Unexpected error during token verification", e);
            return null;
        }
    }

    /**
     * Dev-mode token verification that bypasses JWS signature checking.
     * Manually Base64-decodes the JWT payload and parses it as JSON,
     * allowing any signed or unsigned token to be used for local testing.
     */
    private GoogleUser verifyDev(final String idToken) {
        LOGGER.info("Dev mode active — skipping Google JWT signature verification");
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                LOGGER.warn("Dev token does not have enough parts");
                return null;
            }

            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(decoded, new TypeReference<>() {});

            String sub = (String) payload.get("sub");
            String email = (String) payload.get("email");
            String name = (String) payload.get("name");

            if (sub == null && email == null) {
                LOGGER.warn("Dev token has no sub or email");
                return null;
            }

            return new GoogleUser(
                    sub != null ? sub : email,
                    email != null ? email : sub,
                    name != null ? name : (email != null ? email : sub));
        } catch (Exception e) {
            LOGGER.error("Failed to parse dev token: {}", e.getMessage());
            return null;
        }
    }

    private String extractKid(final String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) return null;

            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            String headerJson = new String(decoded);

            @SuppressWarnings("unchecked")
            Map<String, Object> header = objectMapper.readValue(headerJson, Map.class);
            return (String) header.get("kid");
        } catch (Exception e) {
            LOGGER.error("Failed to extract kid from token", e);
            return null;
        }
    }

    /**
     * Fetches Google's JWKS endpoint and caches the result for 60 minutes.
     * Returns the cached JWKS if still valid.
     *
     * @return the JWKS JSON as a map, or the stale cache on failure
     */
    private Map<String, Object> fetchJwks() {
        Instant now = Instant.now();
        if (cachedJwks != null && jwksFetchedAt != null
                && ChronoUnit.MINUTES.between(jwksFetchedAt, now) < 60) {
            return cachedJwks;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_JWKS_URL))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.error("Failed to fetch JWKS: {}", response.statusCode());
                return cachedJwks;
            }

            TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
            cachedJwks = objectMapper.readValue(response.body(), typeRef);
            jwksFetchedAt = now;
            return cachedJwks;
        } catch (Exception e) {
            LOGGER.error("Failed to fetch JWKS", e);
            return cachedJwks;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findJwkByKid(final String kid) {
        Map<String, Object> jwks = fetchJwks();
        if (jwks == null) return null;

        Object keysObj = jwks.get("keys");
        if (!(keysObj instanceof java.util.List)) return null;

        var keys = (java.util.List<Map<String, Object>>) keysObj;
        for (Map<String, Object> key : keys) {
            if (kid.equals(key.get("kid"))) {
                return key;
            }
        }
        return null;
    }

    /**
     * Reconstructs an RSA public key from a JWK by decoding the Base64url-encoded
     * modulus ({@code n}) and exponent ({@code e}) into a {@link java.security.spec.RSAPublicKeySpec}.
     *
     * @param jwk the JWK map containing {@code n} and {@code e} fields
     * @return the {@link PublicKey}, or {@code null} if the key type is unsupported or decoding fails
     */
    private PublicKey buildPublicKey(final Map<String, Object> jwk) {
        try {
            String kty = (String) jwk.get("kty");
            if (!"RSA".equals(kty)) {
                LOGGER.warn("Unsupported key type: {}", kty);
                return null;
            }

            String nBase64 = (String) jwk.get("n");
            String eBase64 = (String) jwk.get("e");

            if (nBase64 == null || eBase64 == null) {
                LOGGER.warn("Missing modulus or exponent in JWK");
                return null;
            }

            byte[] nBytes = Base64.getUrlDecoder().decode(nBase64);
            byte[] eBytes = Base64.getUrlDecoder().decode(eBase64);

            BigInteger modulus = new BigInteger(1, nBytes);
            BigInteger exponent = new BigInteger(1, eBytes);

            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            LOGGER.error("Failed to build RSA public key", e);
            return null;
        }
    }

    /**
     * Authenticated Google user information extracted from a verified ID token.
     */
    public record GoogleUser(String sub, String email, String name) {
    }
}
