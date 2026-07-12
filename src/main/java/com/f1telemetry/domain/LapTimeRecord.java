package com.f1telemetry.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "lap_time_records")
@Data
@NoArgsConstructor
public class LapTimeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private RaceSession raceSession;

    private int lapNumber;
    private int sector1TimeInMS;
    private int sector2TimeInMS;
    private int sector3TimeInMS;
    private int totalLapTimeInMS;

    // Phase 10: Tyre wear snapshot at lap end (0.0-100.0%), order: RL, RR, FL, FR
    private float tyreWearRL;
    private float tyreWearRR;
    private float tyreWearFL;
    private float tyreWearFR;

    // Phase 10: Fuel and tyre compound at lap end
    private float fuelRemainingKg;   // kg of fuel remaining when lap ended
    private short tyresAgeLaps;       // how many laps on this tyre set
    private short tyreCompound;       // visual compound code (16=soft,17=medium,18=hard,7=inter,8=wet)

    /** Original 6-arg constructor — backward compatible with existing code. */
    public LapTimeRecord(RaceSession raceSession, int lapNumber, int s1, int s2, int s3, int total) {
        this.raceSession = raceSession;
        this.lapNumber = lapNumber;
        this.sector1TimeInMS = s1;
        this.sector2TimeInMS = s2;
        this.sector3TimeInMS = s3;
        this.totalLapTimeInMS = total;
    }

    /** Full constructor including tyre and fuel telemetry snapshot. */
    public LapTimeRecord(RaceSession raceSession, int lapNumber, int s1, int s2, int s3, int total,
                         float tyreWearRL, float tyreWearRR, float tyreWearFL, float tyreWearFR,
                         float fuelRemainingKg, short tyresAgeLaps, short tyreCompound) {
        this.raceSession = raceSession;
        this.lapNumber = lapNumber;
        this.sector1TimeInMS = s1;
        this.sector2TimeInMS = s2;
        this.sector3TimeInMS = s3;
        this.totalLapTimeInMS = total;
        this.tyreWearRL = tyreWearRL;
        this.tyreWearRR = tyreWearRR;
        this.tyreWearFL = tyreWearFL;
        this.tyreWearFR = tyreWearFR;
        this.fuelRemainingKg = fuelRemainingKg;
        this.tyresAgeLaps = tyresAgeLaps;
        this.tyreCompound = tyreCompound;
    }
}
