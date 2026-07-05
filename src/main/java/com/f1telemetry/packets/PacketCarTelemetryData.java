package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Packet Car Telemetry Data - telemetry for all 22 cars.
 * Packet ID: 6
 * Size: 1352 bytes
 * Frequency: Rate as specified in menus
 */
@Data
@NoArgsConstructor
public class PacketCarTelemetryData {
    private PacketHeader header;
    private CarTelemetryData[] carTelemetryData = new CarTelemetryData[22];
    private short mfdPanelIndex;                // uint8 - 255=MFD closed
    private short mfdPanelIndexSecondaryPlayer;  // uint8
    private byte suggestedGear;                 // int8  - 1-8, 0=no suggestion

    public static final int PACKET_ID = 6;
}
