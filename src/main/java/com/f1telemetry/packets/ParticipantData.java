package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Participant Data - per driver information.
 */
@Data
@NoArgsConstructor
public class ParticipantData {
    private short aiControlled;         // uint8 - 0=human, 1=AI
    private short driverId;             // uint8 - 255 if network human
    private short networkId;            // uint8
    private short teamId;               // uint8
    private short myTeam;               // uint8 - 1=My Team
    private short raceNumber;           // uint8
    private short nationality;          // uint8
    private String name;                // char[32] - UTF-8 null terminated
    private short yourTelemetry;        // uint8 - 0=restricted, 1=public
    private short showOnlineNames;      // uint8
    private int techLevel;              // uint16 - F1 World tech level
    private short platform;             // uint8 - 1=Steam, 3=PS, 4=Xbox, 6=Origin
    private short numColours;           // uint8
    private short[][] liveryColours = new short[4][3]; // LiveryColour[4] (RGB)

    public static final int SIZE = 56;
}
