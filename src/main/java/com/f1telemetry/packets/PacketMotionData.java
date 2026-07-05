package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Packet Motion Data - contains all motion data for all cars.
 * Packet ID: 0
 * Size: 1349 bytes
 * Frequency: Rate as specified in menus
 */
@Data
@NoArgsConstructor
public class PacketMotionData {
    private PacketHeader header;
    private CarMotionData[] carMotionData = new CarMotionData[22];

    public static final int PACKET_ID = 0;
}
