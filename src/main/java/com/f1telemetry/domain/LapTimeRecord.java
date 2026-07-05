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

    public LapTimeRecord(RaceSession raceSession, int lapNumber, int s1, int s2, int s3, int total) {
        this.raceSession = raceSession;
        this.lapNumber = lapNumber;
        this.sector1TimeInMS = s1;
        this.sector2TimeInMS = s2;
        this.sector3TimeInMS = s3;
        this.totalLapTimeInMS = total;
    }
}
