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

    private String sessionId;
    private Integer currentLapNum;
    private Long timestamp;

    private Integer speed;
    private Float throttle;
    private Float brake;
    @Column(name = "engine_rpm")
    private Integer engineRPM;

    // Phase 10: Corner zone analysis fields
    private Float lapDistance;   // metres into current lap — key for corner zone detection
    private Float steer;         // steering input (-1.0 left to +1.0 right)
    private Float gForceLateral; // lateral G-force — identifies actual cornering load

    public int getCurrentLapNum() { return currentLapNum != null ? currentLapNum : 0; }
    public long getTimestamp() { return timestamp != null ? timestamp : 0L; }
    public int getSpeed() { return speed != null ? speed : 0; }
    public float getThrottle() { return throttle != null ? throttle : 0.0f; }
    public float getBrake() { return brake != null ? brake : 0.0f; }
    public int getEngineRPM() { return engineRPM != null ? engineRPM : 0; }
    public float getLapDistance() { return lapDistance != null ? lapDistance : 0.0f; }
    public float getSteer() { return steer != null ? steer : 0.0f; }
    public float getGForceLateral() { return gForceLateral != null ? gForceLateral : 0.0f; }

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
