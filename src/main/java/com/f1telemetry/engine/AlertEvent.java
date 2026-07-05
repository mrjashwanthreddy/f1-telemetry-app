package com.f1telemetry.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {
    private String type;     // e.g. "TIRE_OVERHEAT", "DAMAGE"
    private String message;
    private String severity; // "WARNING", "CRITICAL"
    private long timestamp;
}
