package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Packet Lap Data - lap information for all 22 cars.
 * Packet ID: 2
 * Size: 1285 bytes
 * Frequency: Rate as specified in menus
 */
@Data
@NoArgsConstructor
public class PacketLapData {
    private PacketHeader header;
    private LapData[] lapData = new LapData[22];
    private short timeTrialPBCarIdx;        // uint8 - 255 if invalid
    private short timeTrialRivalCarIdx;     // uint8 - 255 if invalid

    public static final int PACKET_ID = 2;
}
