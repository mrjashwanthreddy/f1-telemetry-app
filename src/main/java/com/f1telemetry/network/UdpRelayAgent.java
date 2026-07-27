package com.f1telemetry.network;

import lombok.extern.slf4j.Slf4j;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs inside the desktop .exe (desktop mode only).
 * <p>
 * Listens for F1 game UDP packets on 127.0.0.1:20777 (the game's factory-default
 * telemetry target) and forwards the raw UDP datagrams to the OCI server (140.245.219.62:20777).
 * This allows zero-configuration gameplay for desktop app users while maintaining ultra-low UDP latency.
 */
@Slf4j
public class UdpRelayAgent {

    /** Remote OCI server host and port for UDP forwarding. */
    private static final String RELAY_HOST = System.getProperty("f1.relay.host", "140.245.219.62");
    private static final int RELAY_PORT = Integer.getInteger("f1.relay.port", 20777);

    /** Maximum UDP packet size F1 game ever sends (~1460 bytes). */
    private static final int BUF_SIZE = 2048;

    /** Packets queue — relay thread drains this and sends UDP datagrams to OCI. */
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(1024);

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Shared JWT token set by LocalTokenServer when the user logs in. */
    private final AtomicReference<String> jwtToken;

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

        log.info("[UdpRelayAgent] Started — listening on 127.0.0.1:20777, relaying UDP datagrams to {}:{}", RELAY_HOST, RELAY_PORT);
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
                    // Normal — loop back and check running flag
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                log.error("[UdpRelayAgent] Listener error: {}", e.getMessage());
            }
        }
    }

    // ── UDP Datagram Sender ───────────────────────────────────────────────────

    private void runSender() {
        try (DatagramSocket forwardSocket = new DatagramSocket()) {
            InetAddress targetAddress = InetAddress.getByName(RELAY_HOST);

            while (running.get()) {
                try {
                    byte[] packetData = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (packetData == null) continue;

                    DatagramPacket outPacket = new DatagramPacket(packetData, packetData.length, targetAddress, RELAY_PORT);
                    forwardSocket.send(outPacket);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("[UdpRelayAgent] UDP Forwarder error: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[UdpRelayAgent] Could not initialize UDP forwarder socket: {}", e.getMessage());
        }
    }
}
