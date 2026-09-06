package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TyreStintHistoryData - tyre stint history entry.
 * Size: 3 bytes
 */
@Data
@NoArgsConstructor
public class TyreStintHistoryData {
    private short endLap;               // uint8 - Lap the tyre usage ends on (255 of current tyre)
    private short tyreActualCompound;   // uint8 - Actual tyres used by this driver
    private short tyreVisualCompound;   // uint8 - Visual tyres used by this driver
}
