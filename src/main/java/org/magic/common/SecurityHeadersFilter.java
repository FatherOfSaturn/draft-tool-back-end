package org.magic.common;

import java.io.IOException;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Adds baseline security HTTP headers to every response.
 * These are defense-in-depth measures for browsers:
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — prevents MIME sniffing</li>
 *   <li>{@code X-Frame-Options: DENY} — prevents clickjacking</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — limits referrer leakage</li>
 * </ul>
 */
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(final ContainerRequestContext requestContext,
                       final ContainerResponseContext responseContext) throws IOException {
        responseContext.getHeaders().add("X-Content-Type-Options", "nosniff");
        responseContext.getHeaders().add("X-Frame-Options", "DENY");
        responseContext.getHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");
    }
}
