package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Car Setup Data - per car setup configuration.
 */
@Data
@NoArgsConstructor
public class CarSetupData {
    private short frontWing;                // uint8
    private short rearWing;                 // uint8
    private short onThrottle;               // uint8 - differential percentage
    private short offThrottle;              // uint8 - differential percentage
    private float frontCamber;              // float
    private float rearCamber;               // float
    private float frontToe;                 // float
    private float rearToe;                  // float
    private short frontSuspension;          // uint8
    private short rearSuspension;           // uint8
    private short frontAntiRollBar;         // uint8
    private short rearAntiRollBar;          // uint8
    private short frontSuspensionHeight;    // uint8
    private short rearSuspensionHeight;     // uint8
    private short brakePressure;            // uint8 - percentage
    private short brakeBias;                // uint8 - percentage
    private short engineBraking;            // uint8 - percentage
    private float rearLeftTyrePressure;     // float - PSI
    private float rearRightTyrePressure;    // float - PSI
    private float frontLeftTyrePressure;    // float - PSI
    private float frontRightTyrePressure;   // float - PSI
    private short ballast;                  // uint8
    private float fuelLoad;                 // float

    public static final int SIZE = 49;
}
