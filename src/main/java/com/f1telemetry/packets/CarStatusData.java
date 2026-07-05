package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Car Status Data - per car status information.
 * Size: 55 bytes per car
 */
@Data
@NoArgsConstructor
public class CarStatusData {
    private short tractionControl;          // uint8 - 0=off, 1=medium, 2=full
    private short antiLockBrakes;           // uint8 - 0=off, 1=on
    private short fuelMix;                  // uint8 - 0=lean, 1=standard, 2=rich, 3=max
    private short frontBrakeBias;           // uint8 - percentage
    private short pitLimiterStatus;         // uint8 - 0=off, 1=on
    private float fuelInTank;               // float - current fuel mass
    private float fuelCapacity;             // float
    private float fuelRemainingLaps;        // float - value on MFD
    private int maxRPM;                     // uint16
    private int idleRPM;                    // uint16
    private short maxGears;                 // uint8
    private short drsAllowed;               // uint8 - 0=not allowed, 1=allowed
    private int drsActivationDistance;       // uint16 - metres (0=not available)
    private short actualTyreCompound;       // uint8 - see spec for compound codes
    private short visualTyreCompound;       // uint8 - visual compound
    private short tyresAgeLaps;             // uint8
    private byte vehicleFiaFlags;           // int8  - -1=invalid, 0=none, 1=green, 2=blue, 3=yellow
    private float enginePowerICE;           // float - watts
    private float enginePowerMGUK;          // float - watts
    private float ersStoreEnergy;           // float - joules
    private short ersDeployMode;            // uint8 - 0=none, 1=medium, 2=hotlap, 3=overtake
    private float ersHarvestedThisLapMGUK;  // float
    private float ersHarvestedThisLapMGUH;  // float
    private float ersDeployedThisLap;       // float
    private short networkPaused;            // uint8

    public static final int SIZE = 55;
}
