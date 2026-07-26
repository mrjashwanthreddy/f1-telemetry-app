package com.f1telemetry.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tiny localhost-only HTTP server (port 17777) that runs inside the desktop .exe.
 * <p>
 * Purpose: After the user logs in via the JCEF browser, auth.js calls
 * {@code POST http://127.0.0.1:17777/token} with the JWT string as the body.
 * This server stores it in a shared AtomicReference so the {@link UdpRelayAgent}
 * can authenticate its relay requests to the OCI server.
 * <p>
 * Binds to 127.0.0.1 only — never exposed to the network.
 */
@Slf4j
public class LocalTokenServer {

    private static final int PORT = 17777;
    /** Origin of the OCI server UI — allowed for CORS. */
    private static final String ALLOWED_ORIGIN = "http://f1-telemetry-app.duckdns.org:8080";

    private final AtomicReference<String> tokenRef;
    private HttpServer server;

    public LocalTokenServer(AtomicReference<String> tokenRef) {
        this.tokenRef = tokenRef;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/token", this::handleToken);
            server.setExecutor(null); // uses default executor
            server.start();
            log.info("[LocalTokenServer] Listening on 127.0.0.1:{}", PORT);
        } catch (IOException e) {
            log.error("[LocalTokenServer] Failed to start: {}", e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        // Always respond with CORS headers so the JCEF browser's JS can call us
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            // CORS preflight
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try (InputStream is = exchange.getRequestBody()) {
                String token = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!token.isBlank()) {
                    tokenRef.set(token);
                    log.info("[LocalTokenServer] JWT token received and stored for relay");
                }
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        } else {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
        }
    }
}
