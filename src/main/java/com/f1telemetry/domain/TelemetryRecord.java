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

    public TelemetryRecord(String sessionId, int currentLapNum, long timestamp, int speed, float throttle, float brake, int engineRPM) {
        this.sessionId = sessionId;
        this.currentLapNum = currentLapNum;
        this.timestamp = timestamp;
        this.speed = speed;
        this.throttle = throttle;
        this.brake = brake;
        this.engineRPM = engineRPM;
    }
}
