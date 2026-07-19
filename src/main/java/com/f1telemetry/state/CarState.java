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

    // Status
    private float fuelInTank;
    private float ersStoreEnergy;
    private short visualTyreCompound;
    private short tyresAgeLaps;        // Phase 10: laps on current tyre set
    
    // Damage
    private float[] tyreWear = new float[4];
    private short frontLeftWingDamage;
    private short frontRightWingDamage;

    // Participant details
    private String name;
    private short teamId;

    // Completed lap sector times and personal bests tracked on the backend
    private int lastLapSector1TimeInMS;
    private int lastLapSector2TimeInMS;
    private int lastLapSector3TimeInMS;
    private int bestSector1TimeInMS;
    private int bestSector2TimeInMS;
    private int bestSector3TimeInMS;
    private short resultStatus;

    public CarState(int index) {
        this.carIndex = index;
    }
}
