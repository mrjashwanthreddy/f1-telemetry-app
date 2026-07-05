package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Packet Final Classification Data - end of race classification for all cars.
 * Packet ID: 8
 * Size: 1042 bytes
 * Frequency: Once at the end of a race
 */
@Data
@NoArgsConstructor
public class PacketFinalClassificationData {
    private PacketHeader header;
    private short numCars;                  // uint8
    private FinalClassificationData[] classificationData = new FinalClassificationData[22];

    public static final int PACKET_ID = 8;
}
