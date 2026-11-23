package org.magic.supportService.external;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEFAULTS;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.WebApplicationException;

class TurnstileVerifierTest {

    private static final String SECRET = "test-secret-key";

    private HttpServer server;
    private volatile int stubStatus = 200;
    private volatile String stubBody = "{\"success\":true}";
    private volatile String lastRequestBody;

    private static org.eclipse.microprofile.context.ManagedExecutor inlineExecutor() {
        return mock(org.eclipse.microprofile.context.ManagedExecutor.class,
                invocation -> {
                    if ("execute".equals(invocation.getMethod().getName())) {
                        ((Runnable) invocation.getArgument(0)).run();
                        return null;
                    }
                    return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
                });
    }

    private TurnstileVerifier verifier;
    private TurnstileVerifier disabledVerifier;

    @BeforeEach
    void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siteverify", exchange -> {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = stubBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stubStatus, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify";

        verifier = new TurnstileVerifier(true, java.util.Optional.of(SECRET), url, java.util.Optional.empty(),
                inlineExecutor());
        disabledVerifier = new TurnstileVerifier(false, java.util.Optional.of(SECRET), url, java.util.Optional.empty(),
                inlineExecutor());
    }

    @AfterEach
    void teardown() {
        server.stop(0);
    }

    private void await(final Uni<Void> uni) {
        uni.await().atMost(Duration.ofSeconds(5));
    }

    @Test
    void shouldSkipVerificationWhenDisabled() {
        stubStatus = 500;

        assertDoesNotThrow(() -> await(disabledVerifier.verify("any-token", "203.0.113.5")));
    }

    @Test
    void shouldRejectMissingToken() {
        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> await(verifier.verify(null, "203.0.113.5")));
        assertEquals(400, e.getResponse().getStatus());

        e = assertThrows(WebApplicationException.class, () -> await(verifier.verify("  ", "203.0.113.5")));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldRejectOversizedToken() {
        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> await(verifier.verify("x".repeat(3000), "203.0.113.5")));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldFailClosedWhenSecretMissing() {
        TurnstileVerifier unconfigured = new TurnstileVerifier(true, java.util.Optional.empty(), "http://127.0.0.1:1/siteverify",
                java.util.Optional.empty(), inlineExecutor());

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> await(unconfigured.verify("token", null)));
        assertEquals(500, e.getResponse().getStatus());
    }

    @Test
    void shouldPassOnSuccessfulVerification() {
        stubStatus = 200;
        stubBody = "{\"success\":true,\"hostname\":\"example.com\"}";

        assertDoesNotThrow(() -> await(verifier.verify("good-token", "203.0.113.5")));

        org.junit.jupiter.api.Assertions.assertTrue(lastRequestBody.contains("secret="));
        org.junit.jupiter.api.Assertions.assertTrue(lastRequestBody.contains("response=good-token"));
        org.junit.jupiter.api.Assertions.assertTrue(lastRequestBody.contains("remoteip=203.0.113.5"));
    }

    @Test
    void shouldRejectFailedVerification() {
        stubStatus = 200;
        stubBody = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> await(verifier.verify("bad-token", "203.0.113.5")));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldFailClosedOnServerErrors() {
        stubStatus = 500;
        stubBody = "upstream exploded";

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> await(verifier.verify("token", "203.0.113.5")));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldFailClosedOnUnreachableEndpoint() {
        // Port 1 on localhost: connection refused.
        TurnstileVerifier unreachable = new TurnstileVerifier(true, java.util.Optional.of(SECRET), "http://127.0.0.1:1/siteverify",
                java.util.Optional.empty(), inlineExecutor());

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> await(unreachable.verify("token", "203.0.113.5")));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldFailClosedOnUnparseableBody() {
        stubStatus = 200;
        stubBody = "not json at all";

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> await(verifier.verify("token", "203.0.113.5")));
        assertEquals(400, e.getResponse().getStatus());
    }

    private TurnstileVerifier verifierAllowing(final String allowedHostnames) {
        return new TurnstileVerifier(true, java.util.Optional.of(SECRET),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify",
                java.util.Optional.ofNullable(allowedHostnames), inlineExecutor());
    }

    @Test
    void shouldAcceptRegistrationSiteHostname() {
        TurnstileVerifier strict = verifierAllowing("pyramiddraft.xyz,dev.pyramiddraft.xyz");
        stubStatus = 200;
        stubBody = "{\"success\":true,\"hostname\":\"pyramiddraft.xyz\"}";

        assertDoesNotThrow(() -> await(strict.verify("token", "203.0.113.5")));
    }

    @Test
    void shouldAcceptSecondaryHostname() {
        TurnstileVerifier strict = verifierAllowing("pyramiddraft.xyz,dev.pyramiddraft.xyz");
        stubStatus = 200;
        stubBody = "{\"success\":true,\"hostname\":\"dev.pyramiddraft.xyz\"}";

        assertDoesNotThrow(() -> await(strict.verify("token", "203.0.113.5")));
    }

    @Test
    void shouldMatchHostnameCaseInsensitively() {
        TurnstileVerifier strict = verifierAllowing("PyramidDraft.xyz");
        stubStatus = 200;
        stubBody = "{\"success\":true,\"hostname\":\"PYRAMIDDRAFT.XYZ\"}";

        assertDoesNotThrow(() -> await(strict.verify("token", "203.0.113.5")));
    }

    @Test
    void shouldRejectForeignHostname() {
        TurnstileVerifier strict = verifierAllowing("pyramiddraft.xyz,dev.pyramiddraft.xyz");
        stubStatus = 200;
        stubBody = "{\"success\":true,\"hostname\":\"evil-copy.example\"}";

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> await(strict.verify("token", "203.0.113.5")));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldRejectMissingHostnameWhenListConfigured() {
        TurnstileVerifier strict = verifierAllowing("pyramiddraft.xyz");
        stubStatus = 200;
        stubBody = "{\"success\":true}";

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> await(strict.verify("token", "203.0.113.5")));
        assertEquals(400, e.getResponse().getStatus());
    }

    @Test
    void shouldSkipHostnameCheckWhenListEmpty() {
        TurnstileVerifier lenient = verifierAllowing("");
        stubStatus = 200;
        stubBody = "{\"success\":true,\"hostname\":\"anything.example\"}";

        assertDoesNotThrow(() -> await(lenient.verify("token", "203.0.113.5")));
    }
}
