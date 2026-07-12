package com.f1telemetry.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "telemetry_records")
@Data
@NoArgsConstructor
public class TelemetryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionId; // Simplification: we'll use a string for the session
    private int currentLapNum;
    private long timestamp;

    private int speed;
    private float throttle;
    private float brake;
    private int engineRPM;

    // Phase 10: Corner zone analysis fields
    private float lapDistance;   // metres into current lap — key for corner zone detection
    private float steer;         // steering input (-1.0 left to +1.0 right)
    private float gForceLateral; // lateral G-force — identifies actual cornering load

    public TelemetryRecord(String sessionId, int currentLapNum, long timestamp,
                           int speed, float throttle, float brake, int engineRPM,
                           float lapDistance, float steer, float gForceLateral) {
        this.sessionId = sessionId;
        this.currentLapNum = currentLapNum;
        this.timestamp = timestamp;
        this.speed = speed;
        this.throttle = throttle;
        this.brake = brake;
        this.engineRPM = engineRPM;
        this.lapDistance = lapDistance;
        this.steer = steer;
        this.gForceLateral = gForceLateral;
    }
}
