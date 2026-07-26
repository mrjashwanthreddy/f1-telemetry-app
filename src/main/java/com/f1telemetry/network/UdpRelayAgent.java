package com.f1telemetry.network;

import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs inside the desktop .exe (desktop mode only).
 * <p>
 * Listens for F1 game UDP packets on 127.0.0.1:20777 (the game's factory-default
 * telemetry target) and forwards the raw bytes to the OCI server via HTTP POST.
 * This means users never need to change any game settings — zero configuration.
 * <p>
 * The relay authenticates each request with the logged-in user's JWT token,
 * which is supplied by the local token server ({@link LocalTokenServer}) that
 * auth.js notifies after a successful login.
 */
@Slf4j
public class UdpRelayAgent {

    /** Remote server relay endpoint. */
    private static final String RELAY_URL =
            "http://f1-telemetry-app.duckdns.org:8080/api/relay/udp";

    /** Maximum UDP packet size F1 game ever sends (~1460 bytes). */
    private static final int BUF_SIZE = 2048;

    /** Packets queue — relay thread drains this and sends to OCI. */
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(1024);

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** JWT token set by LocalTokenServer when the user logs in. */
    private final AtomicReference<String> jwtToken;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Thread listenerThread;
    private Thread senderThread;

    public UdpRelayAgent(AtomicReference<String> sharedToken) {
        this.jwtToken = sharedToken;
    }

    /** Start the UDP listener + sender threads. Call once from DesktopLauncher. */
    public void start() {
        if (!running.compareAndSet(false, true)) return;

        listenerThread = new Thread(this::runListener, "udp-relay-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        senderThread = new Thread(this::runSender, "udp-relay-sender");
        senderThread.setDaemon(true);
        senderThread.start();

        log.info("[UdpRelayAgent] Started — listening on 127.0.0.1:20777, relaying to {}", RELAY_URL);
    }

    public void stop() {
        running.set(false);
        if (listenerThread != null) listenerThread.interrupt();
        if (senderThread != null) senderThread.interrupt();
    }

    // ── UDP Listener ──────────────────────────────────────────────────────────

    private void runListener() {
        try (DatagramSocket socket = new DatagramSocket(20777, InetAddress.getByName("127.0.0.1"))) {
            socket.setSoTimeout(1000); // 1 s timeout so we can check running flag
            byte[] buf = new byte[BUF_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (running.get()) {
                try {
                    socket.receive(packet);
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                    if (!queue.offer(data)) {
                        log.warn("[UdpRelayAgent] Queue full — dropping packet");
                    }
                } catch (java.net.SocketTimeoutException ignored) {
                    // Normal — just loop back and check running flag
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                log.error("[UdpRelayAgent] Listener error: {}", e.getMessage());
            }
        }
    }

    // ── HTTP Sender ───────────────────────────────────────────────────────────

    private void runSender() {
        while (running.get()) {
            try {
                byte[] packet = queue.poll(500, TimeUnit.MILLISECONDS);
                if (packet == null) continue;

                String token = jwtToken.get();
                if (token == null || token.isBlank()) {
                    // User not logged in yet — discard and wait
                    queue.clear();
                    continue;
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(RELAY_URL))
                        .header("Content-Type", "application/octet-stream")
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(packet))
                        .timeout(Duration.ofSeconds(3))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                        .exceptionally(ex -> {
                            log.debug("[UdpRelayAgent] Relay send failed: {}", ex.getMessage());
                            return null;
                        });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("[UdpRelayAgent] Sender error: {}", e.getMessage());
            }
        }
    }
}
