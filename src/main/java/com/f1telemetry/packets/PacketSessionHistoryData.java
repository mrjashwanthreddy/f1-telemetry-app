package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PacketSessionHistoryData - Packet ID 11.
 * Details previous laps and tyre stint history for a specific car.
 * Size: 1460 bytes
 */
@Data
@NoArgsConstructor
public class PacketSessionHistoryData {
    public static final int PACKET_ID = 11;

    private PacketHeader header;
    private short carIdx;               // uint8 - Index of the car this lap data relates to
    private short numLaps;              // uint8 - Num laps in the data (including current partial lap)
    private short numTyreStints;        // uint8 - Number of tyre stints in the data
    private short bestLapTimeLapNum;    // uint8 - Lap the best lap time was achieved on
    private short bestSector1LapNum;    // uint8 - Lap the best Sector 1 time was achieved on
    private short bestSector2LapNum;    // uint8 - Lap the best Sector 2 time was achieved on
    private short bestSector3LapNum;    // uint8 - Lap the best Sector 3 time was achieved on

    private LapHistoryData[] lapHistoryData = new LapHistoryData[100];
    private TyreStintHistoryData[] tyreStintsHistoryData = new TyreStintHistoryData[8];
}
