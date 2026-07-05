package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Weather Forecast Sample - part of session packet.
 */
@Data
@NoArgsConstructor
public class WeatherForecastSample {
    private short sessionType;              // uint8
    private short timeOffset;               // uint8 - Time in minutes the forecast is for
    private short weather;                  // uint8 - 0=clear, 1=light cloud, 2=overcast, 3=light rain, 4=heavy rain, 5=storm
    private byte trackTemperature;          // int8  - Track temp in celsius
    private byte trackTemperatureChange;    // int8  - 0=up, 1=down, 2=no change
    private byte airTemperature;            // int8  - Air temp in celsius
    private byte airTemperatureChange;      // int8  - 0=up, 1=down, 2=no change
    private short rainPercentage;           // uint8 - Percentage chance of rain (0-100)

    public static final int SIZE = 8;
}
