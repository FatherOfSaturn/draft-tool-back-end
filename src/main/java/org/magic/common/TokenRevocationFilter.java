package org.magic.common;

import java.io.IOException;

import org.magic.common.api.ErrorMessage;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Rejects requests carrying a revoked bearer token. Runs before JWT
 * authentication so that a logged-out token is refused with 401 regardless of
 * which resource is targeted.
 */
@Provider
public class TokenRevocationFilter implements ContainerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenRevocationService revocationService;

    @Inject
    public TokenRevocationFilter(final TokenRevocationService revocationService) {
        this.revocationService = revocationService;
    }

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        String authorization = requestContext.getHeaderString("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return;
        }
        if (revocationService.isRevoked(token)) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorMessage("Token has been revoked. Please log in again."))
                    .build());
        }
    }
}
