package com.f1telemetry.controller;

import com.f1telemetry.network.UdpPacketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives raw F1 game UDP packets relayed over HTTP from the desktop .exe.
 * <p>
 * The desktop .exe captures UDP from the game on localhost:20777 (the F1
 * factory-default) and POSTs each packet here as raw bytes. This endpoint
 * feeds them into the same {@link UdpPacketHandler} parsing pipeline used
 * for direct UDP reception, so all live state, WebSocket broadcasts, and
 * rule engine logic work identically regardless of the ingestion path.
 * <p>
 * Requires a valid JWT in the Authorization header — the .exe relay supplies
 * this automatically after the user logs in.
 */
@Slf4j
@RestController
@RequestMapping("/api/relay")
@RequiredArgsConstructor
public class UdpRelayController {

    private final UdpPacketHandler udpPacketHandler;

    /**
     * Accepts a single raw F1 game UDP packet as {@code application/octet-stream}.
     * Auth is handled by the global JWT filter — no additional checks needed here.
     */
    @PostMapping(value = "/udp", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> receiveRelayedPacket(@RequestBody byte[] rawPacket) {
        if (rawPacket == null || rawPacket.length == 0) {
            return ResponseEntity.badRequest().build();
        }
        udpPacketHandler.processRawBytes(rawPacket);
        return ResponseEntity.ok().build();
    }
}
