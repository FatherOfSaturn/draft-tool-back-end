package org.magic.it;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * In-process stub of the external HTTP APIs (Scryfall and CubeCobra) used by
 * the reactive REST clients. Serves canned JSON on an ephemeral port so the
 * integration tests exercise the real client stack (config resolution, HTTP,
 * JSON decoding, Uni completion/failure) without network access.
 *
 * <p>Responses are content-dependent rather than counter-based, because the
 * test profile and the application run in different Quarkus classloaders and
 * cannot share mutable static state. Any observable assertion is therefore
 * derived from what the stub returns or how it fails.</p>
 */
final class StubExternalApis {

    private static final String SCRYFALL_PREFIX = "/scryfall";
    private static final String CUBECOBRA_PREFIX = "/cubecobra";

    private final HttpServer server;

    StubExternalApis() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    String scryfallBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + SCRYFALL_PREFIX;
    }

    String cubeCobraBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + CUBECOBRA_PREFIX;
    }

    private void handle(final HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getRawQuery();
            String method = exchange.getRequestMethod();

            if (path.startsWith(SCRYFALL_PREFIX + "/cards/search")) {
                // The paginated search test asserts that page numbers are honoured:
                // page 1 returns the card, page 2 ends the pagination.
                if (query != null && query.contains("page=2")) {
                    respond(exchange, 200, "{\"object\":\"list\",\"data\":[],\"has_more\":false}");
                } else {
                    respond(exchange, 200, "{\"object\":\"list\",\"data\":["
                            + fixture("ScryfallLightningBolt.json") + "],\"has_more\":false,"
                            + "\"total_cards\":1}");
                }
            } else if (path.startsWith(SCRYFALL_PREFIX + "/cards/collection") && "POST".equals(method)) {
                respond(exchange, 200, "{\"data\":[" + fixture("ScryfallLightningBolt.json") + "],"
                        + "\"not_found\":[{\"name\":\"Absent Card\"}]}");
            } else if (path.startsWith(SCRYFALL_PREFIX + "/cards/")) {
                String id = path.substring((SCRYFALL_PREFIX + "/cards/").length());
                if ("missing".equals(id)) {
                    respond(exchange, 404, "{\"object\":\"error\",\"status\":404,\"details\":\"Not found\"}");
                } else {
                    respond(exchange, 200, fixture("ScryfallLightningBolt.json"));
                }
            } else if (path.startsWith(CUBECOBRA_PREFIX + "/cube/api/cubeJSON/")) {
                respond(exchange, 200, fixture("ZachCube.json"));
            } else if (path.startsWith(CUBECOBRA_PREFIX + "/cubes")) {
                respond(exchange, 200, "[" + fixture("ZachCube.json") + "]");
            } else {
                respond(exchange, 404, "{}");
            }
        }
    }

    private static void respond(final HttpExchange exchange, final int status, final String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String fixture(final String name) {
        try (InputStream is = StubExternalApis.class.getClassLoader().getResourceAsStream(name)) {
            if (is == null) {
                throw new IllegalStateException("Missing test fixture " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read fixture " + name, e);
        }
    }
}
