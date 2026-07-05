package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Marshal Zone data - part of session packet.
 */
@Data
@NoArgsConstructor
public class MarshalZone {
    private float zoneStart;    // Fraction (0..1) of way through the lap
    private byte zoneFlag;      // int8: -1=invalid, 0=none, 1=green, 2=blue, 3=yellow

    public static final int SIZE = 5;
}
