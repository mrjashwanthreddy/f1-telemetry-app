package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Car Motion Data - per car motion physics data.
 * Size: 60 bytes per car
 */
@Data
@NoArgsConstructor
public class CarMotionData {
    private float worldPositionX;       // World space X position - metres
    private float worldPositionY;       // World space Y position
    private float worldPositionZ;       // World space Z position
    private float worldVelocityX;       // Velocity in world space X - metres/s
    private float worldVelocityY;       // Velocity in world space Y
    private float worldVelocityZ;       // Velocity in world space Z
    private short worldForwardDirX;     // int16 - World space forward X direction (normalised)
    private short worldForwardDirY;     // int16 - World space forward Y direction (normalised)
    private short worldForwardDirZ;     // int16 - World space forward Z direction (normalised)
    private short worldRightDirX;       // int16 - World space right X direction (normalised)
    private short worldRightDirY;       // int16 - World space right Y direction (normalised)
    private short worldRightDirZ;       // int16 - World space right Z direction (normalised)
    private float gForceLateral;        // Lateral G-Force component
    private float gForceLongitudinal;   // Longitudinal G-Force component
    private float gForceVertical;       // Vertical G-Force component
    private float yaw;                  // Yaw angle in radians
    private float pitch;                // Pitch angle in radians
    private float roll;                 // Roll angle in radians

    public static final int SIZE = 60;
}
