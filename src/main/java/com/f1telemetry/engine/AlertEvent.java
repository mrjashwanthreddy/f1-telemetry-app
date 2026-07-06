package com.f1telemetry.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AlertEvent {
    private String type;      // e.g. "TIRE_OVERHEAT", "PENALTY", "FASTEST_LAP"
    private String message;
    private String severity;  // "INFO", "WARNING", "CRITICAL", "SUCCESS"
    private long timestamp;
    private String detail;    // Optional structured detail (e.g. "327.9" for speed, "1:33.099" for lap time)

    /** Backward-compatible 4-arg constructor used by RuleEvaluationEngine. */
    public AlertEvent(String type, String message, String severity, long timestamp) {
        this.type = type;
        this.message = message;
        this.severity = severity;
        this.timestamp = timestamp;
        this.detail = null;
    }

    /** Full 5-arg constructor used by EventBroadcastService. */
    public AlertEvent(String type, String message, String severity, long timestamp, String detail) {
        this.type = type;
        this.message = message;
        this.severity = severity;
        this.timestamp = timestamp;
        this.detail = detail;
    }
}
