package com.f1telemetry.state;

import lombok.Data;

/**
 * Holds the absolute latest telemetry and lap data for a single car.
 * This object is updated in-place to prevent memory allocation overhead.
 */
@Data
public class CarState {
    private int carIndex;
    
    // Telemetry
    private int speed;
    private int engineRPM;
    private byte gear;
    private float throttle;
    private float brake;
    private float steer;

    // Phase 10: Motion / position data for corner zone analysis
    private float lapDistance;    // metres into current lap (from LapData)
    private float gForceLateral;  // lateral G-force (from CarMotionData)
    
    // Tire Temps (Surface)
    private short[] tyreSurfaceTemps = new short[4]; // RL, RR, FL, FR
    
    // Brake Temps
    private int[] brakesTemperature = new int[4]; // RL, RR, FL, FR
    
    // Lap Data
    private short position;
    private short currentLapNum;
    private short sector;
    private long lastLapTimeInMS;
    private long currentLapTimeInMS;
    private long bestLapTimeInMS;
    private int sector1TimeInMS;
    private int sector2TimeInMS;

    // Realtime Deltas (Interval to car in front, Gap to leader)
    private int deltaToCarInFrontInMS;
    private int deltaToLeaderInMS;

    // Status
    private float fuelInTank;
    private float fuelRemainingLaps;
    private float ersStoreEnergy;
    private short ersDeployMode;       // 0=NONE, 1=MEDIUM, 2=HOTLAP, 3=OVERTAKE
    private short drs;                 // 0=OFF, 1=ON
    private short drsAllowed;          // 0=NOT ALLOWED, 1=ALLOWED
    private short visualTyreCompound;
    private short tyresAgeLaps;        // Phase 10: laps on current tyre set
    
    // Damage
    private float[] tyreWear = new float[4];
    private short frontLeftWingDamage;
    private short frontRightWingDamage;

    // Participant details
    private String name;
    private short teamId;
    private short driverId = -1;

    // Completed lap sector times and personal bests tracked on the backend
    private int lastLapSector1TimeInMS;
    private int lastLapSector2TimeInMS;
    private int lastLapSector3TimeInMS;
    private int bestSector1TimeInMS;
    private int bestSector2TimeInMS;
    private int bestSector3TimeInMS;
    private short resultStatus;

    // ── Car Setup (Packet ID 5) ──────────────────────────────────────────────
    // NOTE: In online multiplayer, other human cars' setups come through as
    // zeroes per F1 25 spec. Only own car + AI cars have real values.
    private short setupFrontWing;               // Aero (0-50)
    private short setupRearWing;                // Aero (0-50)
    private short setupOnThrottle;              // Differential on-throttle %
    private short setupOffThrottle;             // Differential off-throttle %
    private float setupFrontCamber;             // Camber angle (degrees)
    private float setupRearCamber;              // Camber angle (degrees)
    private float setupFrontToe;                // Toe angle (degrees)
    private float setupRearToe;                 // Toe angle (degrees)
    private short setupFrontSuspension;         // Suspension stiffness
    private short setupRearSuspension;          // Suspension stiffness
    private short setupFrontAntiRollBar;        // ARB stiffness
    private short setupRearAntiRollBar;         // ARB stiffness
    private short setupFrontSuspensionHeight;   // Ride height
    private short setupRearSuspensionHeight;    // Ride height
    private short setupBrakePressure;           // Brake pressure %
    private short setupBrakeBias;               // Brake bias %
    private short setupEngineBraking;           // Engine braking %
    private float setupRearLeftTyrePressure;    // PSI
    private float setupRearRightTyrePressure;   // PSI
    private float setupFrontLeftTyrePressure;   // PSI
    private float setupFrontRightTyrePressure;  // PSI
    private short setupBallast;                 // Ballast
    private float setupFuelLoad;                // Fuel load at race start

    // ── ERS Lap Accounting (from CarStatusData) ──────────────────────────────
    private float ersDeployedThisLap;           // Joules deployed this lap
    private float ersHarvestedThisLapMGUK;      // Joules harvested by MGU-K
    private float ersHarvestedThisLapMGUH;      // Joules harvested by MGU-H

    // ── Completed Lap History (from PacketSessionHistoryData) ────────────────
    private java.util.List<CompletedLap> lapHistory = new java.util.ArrayList<>();

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class CompletedLap {
        private int lapNum;
        private long lapTimeInMS;
        private int sector1TimeInMS;
        private int sector2TimeInMS;
        private int sector3TimeInMS;
        private boolean valid;
    }

    public CarState(int index) {
        this.carIndex = index;
    }
}
