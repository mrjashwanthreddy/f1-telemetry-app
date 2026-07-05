package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lap Data - per car lap information.
 * Size: 57 bytes per car
 */
@Data
@NoArgsConstructor
public class LapData {
    private long lastLapTimeInMS;           // uint32
    private long currentLapTimeInMS;        // uint32
    private int sector1TimeMSPart;          // uint16
    private short sector1TimeMinutesPart;   // uint8
    private int sector2TimeMSPart;          // uint16
    private short sector2TimeMinutesPart;   // uint8
    private int deltaToCarInFrontMSPart;    // uint16
    private short deltaToCarInFrontMinutesPart; // uint8
    private int deltaToRaceLeaderMSPart;    // uint16
    private short deltaToRaceLeaderMinutesPart; // uint8
    private float lapDistance;              // float - distance around current lap (metres)
    private float totalDistance;            // float - total distance in session (metres)
    private float safetyCarDelta;           // float - delta in seconds for safety car
    private short carPosition;              // uint8 - race position
    private short currentLapNum;            // uint8
    private short pitStatus;                // uint8 - 0=none, 1=pitting, 2=in pit area
    private short numPitStops;              // uint8
    private short sector;                   // uint8 - 0=sector1, 1=sector2, 2=sector3
    private short currentLapInvalid;        // uint8 - 0=valid, 1=invalid
    private short penalties;                // uint8 - accumulated time penalties (seconds)
    private short totalWarnings;            // uint8
    private short cornerCuttingWarnings;    // uint8
    private short numUnservedDriveThroughPens; // uint8
    private short numUnservedStopGoPens;    // uint8
    private short gridPosition;             // uint8
    private short driverStatus;             // uint8 - 0=garage, 1=flying, 2=in, 3=out, 4=on track
    private short resultStatus;             // uint8 - 0=invalid..7=retired
    private short pitLaneTimerActive;       // uint8
    private int pitLaneTimeInLaneInMS;      // uint16
    private int pitStopTimerInMS;           // uint16
    private short pitStopShouldServePen;    // uint8
    private float speedTrapFastestSpeed;    // float - km/h
    private short speedTrapFastestLap;      // uint8 - 255=not set

    public static final int SIZE = 57;
}
