package org.magic.common;

import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;

/**
 * Adds baseline security HTTP headers to every JAX-RS response.
 * These are defense-in-depth measures for browsers:
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME sniffing</li>
 *   <li>{@code X-Frame-Options: DENY} — prevents clickjacking</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — limits referrer leakage</li>
 * </ul>
 *
 * <p>Implemented as a RESTEasy Reactive server response filter: plain
 * {@code @Provider} JAX-RS filters are not registered by {@code quarkus-rest},
 * so this must stay in the {@code @ServerResponseFilter} form.</p>
 */
@ApplicationScoped
public class SecurityHeadersFilter {

    /**
     * RESTEasy Reactive response filter; mutates the response in place.
     */
    @ServerResponseFilter
    public void filter(final ContainerRequestContext requestContext,
                       final ContainerResponseContext responseContext) {
        responseContext.getHeaders().add("X-Content-Type-Options", "nosniff");
        responseContext.getHeaders().add("X-Frame-Options", "DENY");
        responseContext.getHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");
    }
}
