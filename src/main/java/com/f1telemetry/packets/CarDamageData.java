package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Car Damage Data - per car damage information.
 * Packet ID: 10
 */
@Data
@NoArgsConstructor
public class CarDamageData {
    private float[] tyresWear = new float[4];           // float[4] - tyre wear percentage
    private short[] tyresDamage = new short[4];         // uint8[4]
    private short[] brakesDamage = new short[4];        // uint8[4]
    private short frontLeftWingDamage;                  // uint8
    private short frontRightWingDamage;                 // uint8
    private short rearWingDamage;                       // uint8
    private short floorDamage;                          // uint8
    private short diffuserDamage;                       // uint8
    private short sidepodDamage;                        // uint8
    private short drsFault;                             // uint8 - 0=OK, 1=fault
    private short ersFault;                             // uint8 - 0=OK, 1=fault
    private short gearBoxDamage;                        // uint8
    private short engineDamage;                         // uint8
    private short engineMGUHWear;                       // uint8
    private short engineESWear;                         // uint8
    private short engineCEWear;                         // uint8
    private short engineICEWear;                        // uint8
    private short engineMGUKWear;                       // uint8
    private short engineTCWear;                         // uint8
    private short engineBlown;                          // uint8 - 0=OK, 1=blown
    private short engineSeized;                         // uint8 - 0=OK, 1=seized

    public static final int SIZE = 42;
}
