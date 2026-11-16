package org.magic.common;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;

/**
 * Resolves the real client IP for rate limiting and abuse-prevention checks.
 *
 * <p>The {@code X-Forwarded-For} header is honored only when the direct peer
 * is a trusted proxy listed in {@code rate-limit.trusted-proxies};
 * client-supplied header values are otherwise ignored and the socket peer
 * address is used. When {@code rate-limit.trusted-proxies} is empty the header
 * is never trusted.</p>
 *
 * <p>Shared by {@link RateLimitFilter} and the support-form anti-spam checks
 * (Turnstile {@code remoteip}) so both see identical client identity
 * semantics.</p>
 */
@ApplicationScoped
public class ClientIpResolver {

    static final String FORWARDED_HEADER = "X-Forwarded-For";
    static final String REMOTE_ADDRESS_PROPERTY = "quarkus.remote-address";

    private final boolean trustForwardedHeader;
    private final Set<String> trustedProxies;

    public ClientIpResolver(
            @ConfigProperty(name = "rate-limit.trust-forwarded-header", defaultValue = "false") final boolean trustForwardedHeader,
            @ConfigProperty(name = "rate-limit.trusted-proxies", defaultValue = "") final Optional<String> trustedProxies) {
        this.trustForwardedHeader = trustForwardedHeader;
        this.trustedProxies = trustedProxies
                .filter(value -> !value.isBlank())
                .map(value -> (Set<String>) Set.copyOf(Arrays.asList(value.split(","))))
                .orElseGet(Set::of);
    }

    /**
     * Resolves the client IP from a REST request filter context.
     *
     * @param requestContext the JAX-RS request context
     * @return the client IP, or a non-null fallback (request URI host) when the
     *         peer address is unavailable
     */
    public String fromRequestContext(final ContainerRequestContext requestContext) {
        Object property = requestContext.getProperty(REMOTE_ADDRESS_PROPERTY);
        String remote = property instanceof String address && !address.isBlank() ? address : null;
        return resolve(remote,
                () -> requestContext.getHeaders() == null ? null
                        : requestContext.getHeaders().get(FORWARDED_HEADER),
                () -> requestContext.getUriInfo() == null ? null
                        : requestContext.getUriInfo().getRequestUri().getHost());
    }

    /**
     * Resolves the client IP from the Vert.x routing context of a resource
     * method.
     *
     * @param routingContext the routing context
     * @return the client IP, or {@code null} when the peer address is unavailable
     */
    public String fromRoutingContext(final RoutingContext routingContext) {
        var socketAddress = routingContext.request().remoteAddress();
        String remote = socketAddress instanceof java.net.InetSocketAddress inet
                ? inet.getHostString()
                : null;
        return resolve(remote, () -> {
            List<String> forwarded = routingContext.request().headers().getAll(FORWARDED_HEADER);
            return forwarded == null || forwarded.isEmpty() ? null : forwarded;
        }, () -> null);
    }

    private String resolve(final String remote, final java.util.function.Supplier<List<String>> forwardedSupplier,
                           final java.util.function.Supplier<String> fallback) {
        if (trustForwardedHeader && remote != null && isTrustedProxy(remote)) {
            List<String> forwarded = forwardedSupplier.get();
            if (forwarded != null && !forwarded.isEmpty() && forwarded.get(0) != null
                    && !forwarded.get(0).isBlank()) {
                return forwarded.get(0).trim();
            }
        }
        if (remote != null) {
            return remote;
        }
        return fallback.get();
    }

    private boolean isTrustedProxy(final String remote) {
        return !trustedProxies.isEmpty() && trustedProxies.contains(remote.trim());
    }
}
