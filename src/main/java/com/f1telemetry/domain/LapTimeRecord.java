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

    private Integer lapNumber;
    private Integer sector1TimeInMS;
    private Integer sector2TimeInMS;
    private Integer sector3TimeInMS;
    private Integer totalLapTimeInMS;

    // Phase 10: Tyre wear snapshot at lap end (0.0-100.0%), order: RL, RR, FL, FR
    private Float tyreWearRL;
    private Float tyreWearRR;
    private Float tyreWearFL;
    private Float tyreWearFR;

    // Phase 10: Fuel and tyre compound at lap end
    private Float fuelRemainingKg;   // kg of fuel remaining when lap ended
    private Short tyresAgeLaps;       // how many laps on this tyre set
    private Short tyreCompound;       // visual compound code (16=soft,17=medium,18=hard,7=inter,8=wet)

    public int getLapNumber() { return lapNumber != null ? lapNumber : 0; }
    public int getSector1TimeInMS() { return sector1TimeInMS != null ? sector1TimeInMS : 0; }
    public int getSector2TimeInMS() { return sector2TimeInMS != null ? sector2TimeInMS : 0; }
    public int getSector3TimeInMS() { return sector3TimeInMS != null ? sector3TimeInMS : 0; }
    public int getTotalLapTimeInMS() { return totalLapTimeInMS != null ? totalLapTimeInMS : 0; }
    public float getTyreWearRL() { return tyreWearRL != null ? tyreWearRL : 0.0f; }
    public float getTyreWearRR() { return tyreWearRR != null ? tyreWearRR : 0.0f; }
    public float getTyreWearFL() { return tyreWearFL != null ? tyreWearFL : 0.0f; }
    public float getTyreWearFR() { return tyreWearFR != null ? tyreWearFR : 0.0f; }
    public float getFuelRemainingKg() { return fuelRemainingKg != null ? fuelRemainingKg : 0.0f; }
    public short getTyresAgeLaps() { return tyresAgeLaps != null ? tyresAgeLaps : (short) 0; }
    public short getTyreCompound() { return tyreCompound != null ? tyreCompound : (short) 0; }

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
