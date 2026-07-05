package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Car Telemetry Data - per car telemetry values.
 * Size: 60 bytes per car
 */
@Data
@NoArgsConstructor
public class CarTelemetryData {
    private int speed;                          // uint16 - km/h
    private float throttle;                     // float  - 0.0 to 1.0
    private float steer;                        // float  - -1.0 (left) to 1.0 (right)
    private float brake;                        // float  - 0.0 to 1.0
    private short clutch;                       // uint8  - 0 to 100
    private byte gear;                          // int8   - 1-8, N=0, R=-1
    private int engineRPM;                      // uint16
    private short drs;                          // uint8  - 0=off, 1=on
    private short revLightsPercent;             // uint8  - percentage
    private int revLightsBitValue;              // uint16 - bit 0=leftmost LED
    private int[] brakesTemperature = new int[4];     // uint16[4] - celsius (RL, RR, FL, FR)
    private short[] tyresSurfaceTemperature = new short[4]; // uint8[4] - celsius
    private short[] tyresInnerTemperature = new short[4];   // uint8[4] - celsius
    private int engineTemperature;              // uint16 - celsius
    private float[] tyresPressure = new float[4];     // float[4] - PSI
    private short[] surfaceType = new short[4];       // uint8[4] - driving surface

    public static final int SIZE = 60;
}
