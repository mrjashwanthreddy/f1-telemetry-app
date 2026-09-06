package com.f1telemetry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WeatherForecastDTO - Data transfer object for weather timeline forecast samples.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherForecastDTO {
    private short timeOffset;          // Minutes into future (0, 5, 10, 15, etc.)
    private short weather;             // 0=Clear, 1=Light Cloud, 2=Overcast, 3=Light Rain, 4=Heavy Rain, 5=Storm
    private byte trackTemperature;     // Celsius
    private byte airTemperature;       // Celsius
    private short rainPercentage;      // 0 - 100%
}
