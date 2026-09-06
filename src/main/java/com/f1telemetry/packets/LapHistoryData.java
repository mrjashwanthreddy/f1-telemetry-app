package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LapHistoryData - historical lap data for a car.
 * Size: 14 bytes
 */
@Data
@NoArgsConstructor
public class LapHistoryData {
    private long lapTimeInMS;           // uint32 - Lap time in milliseconds
    private int sector1TimeMSPart;      // uint16 - Sector 1 milliseconds part
    private short sector1TimeMinutesPart;// uint8 - Sector 1 whole minute part
    private int sector2TimeMSPart;      // uint16 - Sector 2 milliseconds part
    private short sector2TimeMinutesPart;// uint8 - Sector 2 whole minute part
    private int sector3TimeMSPart;      // uint16 - Sector 3 milliseconds part
    private short sector3TimeMinutesPart;// uint8 - Sector 3 whole minute part
    private short lapValidBitFlags;     // uint8 - 0x01 lap valid, 0x02 s1 valid, 0x04 s2 valid, 0x08 s3 valid

    public int getSector1TimeInMS() {
        return sector1TimeMinutesPart * 60000 + sector1TimeMSPart;
    }

    public int getSector2TimeInMS() {
        return sector2TimeMinutesPart * 60000 + sector2TimeMSPart;
    }

    public int getSector3TimeInMS() {
        return sector3TimeMinutesPart * 60000 + sector3TimeMSPart;
    }
}
