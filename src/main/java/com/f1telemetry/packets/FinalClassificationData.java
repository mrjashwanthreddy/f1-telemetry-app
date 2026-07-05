package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Final Classification Data - per car end-of-race results.
 */
@Data
@NoArgsConstructor
public class FinalClassificationData {
    private short position;                 // uint8
    private short numLaps;                  // uint8
    private short gridPosition;             // uint8
    private short points;                   // uint8
    private short numPitStops;              // uint8
    private short resultStatus;             // uint8 - 0=invalid..7=retired
    private short resultReason;             // uint8
    private long bestLapTimeInMS;           // uint32
    private double totalRaceTime;           // double - seconds without penalties
    private short penaltiesTime;            // uint8 - seconds
    private short numPenalties;             // uint8
    private short numTyreStints;            // uint8
    private short[] tyreStintsActual = new short[8];    // uint8[8]
    private short[] tyreStintsVisual = new short[8];    // uint8[8]
    private short[] tyreStintsEndLaps = new short[8];   // uint8[8]

    public static final int SIZE = 45;
}
