package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Packet Car Setup Data - car setups for all 22 cars.
 * Packet ID: 5
 * Size: 1133 bytes
 * Frequency: 2 per second
 */
@Data
@NoArgsConstructor
public class PacketCarSetupData {
    private PacketHeader header;
    private CarSetupData[] carSetups = new CarSetupData[22];
    private float nextFrontWingValue;       // float - value after next pit stop (player only)

    public static final int PACKET_ID = 5;
}
