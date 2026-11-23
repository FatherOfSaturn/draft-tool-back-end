package org.magic.common;

import org.magic.common.api.ErrorMessage;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

/**
 * Rejects requests carrying a revoked bearer token. Runs before JWT
 * authentication so that a logged-out token is refused with 401 regardless of
 * which resource is targeted.
 *
 * <p>Implemented as a RESTEasy Reactive server request filter: plain
 * {@code @Provider} JAX-RS filters are not registered by {@code quarkus-rest},
 * so this must stay in the {@code @ServerRequestFilter} form.</p>
 */
@ApplicationScoped
public class TokenRevocationFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenRevocationService revocationService;

    @Inject
    public TokenRevocationFilter(final TokenRevocationService revocationService) {
        this.revocationService = revocationService;
    }

    /**
     * RESTEasy Reactive request filter: returns the aborting {@link Response}
     * when the token is revoked, {@code null} to continue.
     */
    @ServerRequestFilter(priority = 100)
    public Response filter(final ContainerRequestContext requestContext) {
        String authorization = requestContext.getHeaderString("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        if (revocationService.isRevoked(token)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorMessage("Token has been revoked. Please log in again."))
                    .build();
        }
        return null;
    }
}
