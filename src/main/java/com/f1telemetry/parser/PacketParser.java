package com.f1telemetry.parser;

import com.f1telemetry.packets.*;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parses raw UDP ByteBuf into typed F1 25 packet objects.
 * Reads the 29-byte header first, then routes to specific deserializers.
 */
@Slf4j
@Component
public class PacketParser {

    private final PacketDeserializer deserializer = new PacketDeserializer();

    /**
     * Minimum expected packet sizes (header + body) for each packet type.
     * Used to reject undersized packets before deserialization.
     */
    private static final java.util.Map<Integer, Integer> MIN_PACKET_SIZES = java.util.Map.ofEntries(
            java.util.Map.entry(PacketIds.MOTION, 1349),
            java.util.Map.entry(PacketIds.SESSION, 753),
            java.util.Map.entry(PacketIds.LAP_DATA, 1285),
            java.util.Map.entry(PacketIds.EVENT, 45),
            java.util.Map.entry(PacketIds.PARTICIPANTS, 1284),
            java.util.Map.entry(PacketIds.CAR_SETUPS, 1133),
            java.util.Map.entry(PacketIds.CAR_TELEMETRY, 1352),
            java.util.Map.entry(PacketIds.CAR_STATUS, 1239),
            java.util.Map.entry(PacketIds.FINAL_CLASSIFICATION, 1042),
            java.util.Map.entry(PacketIds.CAR_DAMAGE, 10)
    );

    /**
     * Parse a raw ByteBuf into the appropriate packet object.
     * @param buf the raw UDP packet bytes (Little-Endian)
     * @return parsed packet object, or null if packet type is unsupported or undersized
     */
    public Object parse(ByteBuf buf) {
        int totalBytes = buf.readableBytes();
        if (totalBytes < PacketHeader.SIZE) {
            log.warn("Packet too small: {} bytes (minimum {} required)", totalBytes, PacketHeader.SIZE);
            return null;
        }

        PacketHeader header = deserializer.deserializeHeader(buf);

        // Validate remaining buffer has enough data for this packet type
        Integer minSize = MIN_PACKET_SIZES.get((int) header.getPacketId());
        if (minSize != null && totalBytes < minSize) {
            log.debug("Undersized packet ID {}: {} bytes (expected >= {})", header.getPacketId(), totalBytes, minSize);
            return null;
        }

        return switch (header.getPacketId()) {
            case PacketIds.MOTION -> deserializer.deserializeMotion(header, buf);
            case PacketIds.SESSION -> deserializer.deserializeSession(header, buf);
            case PacketIds.LAP_DATA -> deserializer.deserializeLapData(header, buf);
            case PacketIds.EVENT -> deserializer.deserializeEvent(header, buf);
            case PacketIds.PARTICIPANTS -> deserializer.deserializeParticipants(header, buf);
            case PacketIds.CAR_SETUPS -> deserializer.deserializeCarSetups(header, buf);
            case PacketIds.CAR_TELEMETRY -> deserializer.deserializeCarTelemetry(header, buf);
            case PacketIds.CAR_STATUS -> deserializer.deserializeCarStatus(header, buf);
            case PacketIds.FINAL_CLASSIFICATION -> deserializer.deserializeFinalClassification(header, buf);
            case PacketIds.CAR_DAMAGE -> deserializer.deserializeCarDamage(header, buf);
            default -> {
                log.debug("Unsupported packet ID: {}", header.getPacketId());
                yield null;
            }
        };
    }
}
