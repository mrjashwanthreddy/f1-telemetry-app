package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Packet Car Status Data - status for all 22 cars.
 * Packet ID: 7
 * Size: 1239 bytes
 * Frequency: Rate as specified in menus
 */
@Data
@NoArgsConstructor
public class PacketCarStatusData {
    private PacketHeader header;
    private CarStatusData[] carStatusData = new CarStatusData[22];

    public static final int PACKET_ID = 7;
}
